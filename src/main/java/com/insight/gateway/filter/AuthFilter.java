package com.insight.gateway.filter;

import com.insight.gateway.common.ReplyHelper;
import com.insight.gateway.common.Verify;
import com.insight.utils.DateTime;
import com.insight.utils.EnvUtil;
import com.insight.utils.Json;
import com.insight.utils.Util;
import com.insight.utils.http.HttpUtil;
import com.insight.utils.pojo.auth.InterfaceDto;
import com.insight.utils.pojo.auth.LoginInfo;
import com.insight.utils.pojo.base.Reply;
import com.insight.utils.redis.HashOps;
import com.insight.utils.redis.KeyOps;
import com.insight.utils.redis.StringOps;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * @author 宣炳刚
 * @date 2019-08-29
 * @remark 身份验证及鉴权过滤器
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    /**
     * 令牌持有人信息
     */
    private LoginInfo loginInfo;

    /**
     * 验证结果
     */
    private Reply reply;

    /**
     * 用户特征串
     */
    private String fingerprint;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 限流键名
     */
    private String limitKey;

    /**
     * 身份验证及鉴权过滤器
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @return Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var headers = request.getHeaders();
        var response = exchange.getResponse();
        var responseHeaders = response.getHeaders();

        fingerprint = headers.getFirst("fingerprint");
        requestId = headers.getFirst("requestId");
        var method = request.getMethod();

        var path = request.getPath().value();
        var key = method + ":" + path;
        var config = getConfig(method, path);
        if (config == null) {
            reply = ReplyHelper.fail(requestId, "不存在的URL: " + key);
            return initResponse(exchange);
        }

        // 接口限流
        if (isLimited(config, key)) {
            return initResponse(exchange);
        }

        // 验证提交数据临时Token
        if (config.getNeedToken()) {
            var redisKey = "SubmitToken:" + Util.md5(loginInfo.getId() + ":" + key);
            var submitToken = headers.getFirst("SubmitToken");
            var id = StringOps.get(redisKey);
            if (!Util.isNotEmpty(id) || !id.equals(submitToken)) {
                reply = ReplyHelper.fail(requestId, "SubmitToken不存在");
                return initResponse(exchange);
            } else {
                KeyOps.delete(redisKey);
            }
        }

        // 设置打印返回值标志, 放行公共接口
        exchange.getAttributes().put("logResult", config.getLogResult());
        if (!config.getVerify()) {
            return chain.filter(exchange);
        }

        // 私有接口验证Token,授权接口鉴权
        var token = headers.getFirst("Authorization");
        if (Util.isEmpty(token)) {
            reply = ReplyHelper.invalidToken(requestId);
            return initResponse(exchange);
        }

        var verify = new Verify(requestId, token, fingerprint);
        reply = verify.compare(config.getAuthCode());
        if (!reply.getSuccess()) {
            KeyOps.delete("Surplus:" + limitKey);
            return initResponse(exchange);
        }

        // 请求头附加用户信息
        loginInfo = verify.getLoinInfo();
        request.mutate().header("loginInfo", Json.toBase64(loginInfo)).build();
        return chain.filter(exchange);
    }

    /**
     * 获取过滤器序号
     *
     * @return 过滤器序号
     */
    @Override
    public int getOrder() {
        return 1;
    }

    /**
     * 是否被限流
     *
     * @param config 接口配置
     * @param key    键值
     * @return 是否被限流
     */
    private boolean isLimited(InterfaceDto config, String key) {
        if (!config.getLimit() || !Util.isNotEmpty(key)) {
            return false;
        }

        limitKey = Util.md5(fingerprint + "|" + key);
        return isGapLimited(config) || isCycleLimited(config);
    }

    /**
     * 是否被限流(访问间隔小于最小时间间隔)
     *
     * @param config 接口配置
     * @return 是否限制访问
     */
    private boolean isGapLimited(InterfaceDto config) {
        var gap = Long.valueOf(config.getLimitGap());
        if (0 >= gap) {
            return false;
        }

        var key = "Surplus:" + limitKey;
        var now = DateTime.formatCurrentTime();
        var value = StringOps.get(key);
        if (!Util.isNotEmpty(value)) {
            StringOps.set(key, now, gap);
            return false;
        }

        // 调用时间间隔低于1秒时,重置调用时间为当前时间作为惩罚
        var time = DateTime.parseDateTime(value);
        if (LocalDateTime.now().isBefore(time.plusSeconds(1))) {
            StringOps.set(key, now, gap);
        }

        reply = ReplyHelper.tooOften(requestId, config.getMessage());
        return true;
    }

    /**
     * 是否被限流(限流计时周期内超过最大访问次数)
     *
     * @param config 接口配置
     * @return 是否限制访问
     */
    private Boolean isCycleLimited(InterfaceDto config) {
        var cycle = Long.valueOf(config.getLimitCycle());
        var max = Long.valueOf(config.getLimitMax());
        if (0 >= cycle || 0 >= max) {
            return false;
        }

        var key = "Limit:" + limitKey;
        var val = StringOps.get(key);
        if (Util.isEmpty(val)) {
            StringOps.set(key, "1", cycle);
            return false;
        }

        // 读取访问次数,如次数超过限制,返回true,否则访问次数增加1次
        var count = Integer.parseInt(val);
        if (count > max) {
            reply = ReplyHelper.tooOften(requestId, config.getMessage());
            return true;
        }

        StringOps.set(key, count + 1);
        return false;
    }

    /**
     * 生成返回数据
     *
     * @param exchange ServerWebExchange
     * @return Mono
     */
    private Mono<Void> initResponse(ServerWebExchange exchange) {
        //设置body
        reply.setOption(requestId);
        var json = Json.toJson(reply);
        var data = json.getBytes();
        var response = exchange.getResponse();
        var body = response.bufferFactory().wrap(data);

        //设置headers
        var httpHeaders = response.getHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setDate(System.currentTimeMillis());

        return response.writeWith(Flux.just(body));
    }

    /**
     * 通过匹配URL获取接口配置
     *
     * @param method 请求方法
     * @param uri    请求URL
     * @return 接口配置
     */
    private InterfaceDto getConfig(HttpMethod method, String uri) {
        var key = "Config:Interface";
        var url = uri.replaceAll("/([0-9a-f]{32}|[0-9]{1,19})", "/{}");
        var hash = Util.md5(method.name() + ":" + url);
        if (HashOps.hasKey(key, hash)) {
            return HashOps.get(key, hash, InterfaceDto.class);
        }

        HttpUtil.get(EnvUtil.getValue("insight.loadInterface"), Reply.class);
        return HashOps.get(key, hash, InterfaceDto.class);
    }
}

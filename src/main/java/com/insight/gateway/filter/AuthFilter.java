package com.insight.gateway.filter;

import com.insight.gateway.common.ReplyHelper;
import com.insight.gateway.common.Verify;
import com.insight.utils.DateTime;
import com.insight.utils.Json;
import com.insight.utils.Util;
import com.insight.utils.pojo.auth.InterfaceDto;
import com.insight.utils.pojo.auth.LoginInfo;
import com.insight.utils.pojo.base.Reply;
import com.insight.utils.redis.Redis;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 宣炳刚
 * @date 2019-08-29
 * @remark 身份验证及鉴权过滤器
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private LocalDateTime flagTime;
    private List<InterfaceDto> regConfigs;
    private Map<String, InterfaceDto> hashConfigs;
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

        // 设置打印返回值标志
        exchange.getAttributes().put("logResult", config.getLogResult());

        // 接口限流
        if (isLimited(config, key)) {
            return initResponse(exchange);
        }

        // 验证提交数据临时Token
        if (config.getNeedToken()) {
            var redisKey = "SubmitToken:" + Util.md5(loginInfo.getId() + ":" + key);
            var submitToken = headers.getFirst("SubmitToken");
            var id = Redis.get(redisKey);
            if (!Util.isNotEmpty(id) || !id.equals(submitToken)) {
                reply = ReplyHelper.fail(requestId, "SubmitToken不存在");
                return initResponse(exchange);
            } else {
                Redis.deleteKey(redisKey);
            }
        }

        // 放行公共接口
        if (!config.getVerify()) {
            return chain.filter(exchange);
        }

        // 私有接口验证Token,授权接口鉴权
        var token = headers.getFirst("Authorization");
        if (Util.isEmpty(token)) {
            reply = ReplyHelper.fail(requestId, "Token不存在");
            return initResponse(exchange);
        }

        var verify = new Verify(requestId, token, fingerprint);
        reply = verify.compare(config.getAuthCode());
        if (!reply.getSuccess()) {
            Redis.deleteKey("Surplus:" + limitKey);
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
     * @param config 接口配置表
     * @param key    键值
     * @return 是否被限流
     */
    private boolean isLimited(InterfaceDto config, String key) {
        if (!config.getLimit() || !Util.isNotEmpty(key)) {
            return false;
        }

        limitKey = Util.md5(fingerprint + "|" + key);
        if (isLimited(config.getLimitGap())) {
            return true;
        }

        return isLimited(config.getLimitCycle(), config.getLimitMax(), config.getMessage());
    }

    /**
     * 是否被限流(访问间隔小于最小时间间隔)
     *
     * @param gap 访问最小时间间隔
     * @return 是否限制访问
     */
    private boolean isLimited(long gap) {
        if (0 >= gap) {
            return false;
        }

        var key = "Surplus:" + limitKey;
        var now = DateTime.formatCurrentTime();
        var value = Redis.get(key);
        if (!Util.isNotEmpty(value)) {
            Redis.set(key, now, gap);
            return false;
        }

        // 调用时间间隔低于1秒时,重置调用时间为当前时间作为惩罚
        var time = DateTime.parseDateTime(value);
        if (LocalDateTime.now().isBefore(time.plusSeconds(1))) {
            Redis.set(key, now, gap);
        }

        reply = ReplyHelper.tooOften(requestId);
        return true;
    }

    /**
     * 是否被限流(限流计时周期内超过最大访问次数)
     *
     * @param cycle 限流计时周期(秒)
     * @param max   限制次数/限流周期
     * @param msg   消息
     * @return 是否限制访问
     */
    private Boolean isLimited(Integer cycle, Integer max, String msg) {
        if (0 >= cycle || 0 >= max) {
            return false;
        }

        var key = "Limit:" + limitKey;
        var val = Redis.get(key);
        if (!Util.isNotEmpty(val)) {
            Redis.set(key, "1", cycle.longValue());

            return false;
        }

        // 读取访问次数,如次数超过限制,返回true,否则访问次数增加1次
        var count = Integer.parseInt(val);
        if (count > max) {
            reply = ReplyHelper.tooOften(requestId, msg);
            return true;
        }

        var expire = Redis.getExpire(key);
        count++;
        Redis.set(key, String.valueOf(count), expire);

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
     * @param url    请求URL
     * @return 接口配置
     */
    private InterfaceDto getConfig(HttpMethod method, String url) {
        var now = LocalDateTime.now();
        if (flagTime == null || now.isAfter(flagTime.plusSeconds(60))) {
            flagTime = now;
            hashConfigs = getHashConfigs();
            regConfigs = getRegularConfigs();
        }

        // 先进行哈希匹配
        var path = method.name() + ":" + url;
        var config = hashConfigs.getOrDefault(Util.md5(path), null);

        // 哈希匹配失败后进行正则匹配
        return config == null ? regConfigs.stream().filter(i -> path.matches(i.getRegular())).findAny().orElse(null) : config;
    }

    /**
     * 获取接口配置哈希表
     *
     * @return 接口配置表
     */
    private Map<String, InterfaceDto> getHashConfigs() {
        var json = Redis.get("Config:Interface");
        List<InterfaceDto> list = Json.toList(json, InterfaceDto.class);
        Map<String, InterfaceDto> map = new HashMap<>(list.size());
        for (var config : list) {
            var url = config.getUrl();
            if (!url.contains("{")) {
                var hash = Util.md5(config.getMethod() + ":" + config.getUrl());
                map.put(hash, config);
            }
        }

        return map;
    }

    /**
     * 获取接口配置正则表
     *
     * @return 接口配置表
     */
    private List<InterfaceDto> getRegularConfigs() {
        var json = Redis.get("Config:Interface");
        List<InterfaceDto> list = Json.toList(json, InterfaceDto.class);
        for (var config : list) {
            var method = config.getMethod();
            var url = config.getUrl();
            if (url.contains("{")) {
                // 此正则表达式仅支持32位UUID或整形/长整形数字作为路径参数,如使用其他类型的参数.请修改正则表达式以匹配参数类型
                var regular = method + ":" + url.replaceAll("/\\{[a-zA-Z]+}", "/([0-9a-f]{32}|[0-9]{1,19})");
                config.setRegular(regular);
            }
        }

        return list.stream().filter(i -> i.getRegular() != null).collect(Collectors.toList());
    }
}

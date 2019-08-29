package com.insight.gateway.filter;

import com.insight.gateway.common.InterfaceConfig;
import com.insight.gateway.common.Verify;
import com.insight.util.*;
import com.insight.util.pojo.LoginInfo;
import com.insight.util.pojo.Reply;
import com.insight.util.pojo.TokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author 宣炳刚
 * @date 2019-08-29
 * @remark 身份验证及鉴权过滤器
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final List<InterfaceConfig> configs = getConfigs();

    /**
     * 令牌持有人信息
     */
    private LoginInfo loginInfo = new LoginInfo();

    /**
     * 验证结果
     */
    private Reply reply;

    /**
     * 身份验证及鉴权过滤器
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @return Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        HttpMethod method = request.getMethod();
        RequestPath path = request.getPath();
        InterfaceConfig config = getConfig(method, path.value());

        // 验证及鉴权
        String token = headers.getFirst("Authorization");
        String fingerprint = headers.getFirst("fingerprint");
        if (config.getType() > 0 && verify(token, fingerprint, config.getAuthCode())) {
            return initResponse(exchange);
        }

        if (config.getLimitType().equals(0)) {
            return chain.filter(exchange);
        }

        // 接口限流
        Integer gap = config.getLimitGap();
        Integer cycle = config.getLimitCycle();
        Integer max = config.getLimitMax();
        String key = Util.md5(config.getLimitType().equals(1) ? path.toString() : request.getBody().toString());
        if (isLimited(key, gap, cycle, max)) {
            return initResponse(exchange);
        }

        return chain.filter(exchange);
    }

    /**
     * 获取过滤器序号
     *
     * @return 过滤器序号
     */
    @Override
    public int getOrder() {
        return 2;
    }

    /**
     * 通过匹配URL获取接口配置
     *
     * @param method 请求方法
     * @param url    请求URL
     * @return 接口配置
     */
    private InterfaceConfig getConfig(HttpMethod method, String url) {
        return null;
    }

    /**
     * 验证用户令牌并鉴权
     *
     * @param token       令牌
     * @param fingerprint 用户特征串
     * @param key         操作权限代码
     * @return 是否通过验证
     */
    private boolean verify(String token, String fingerprint, String key) {
        Verify verify = new Verify(token, fingerprint);
        reply = verify.compare(key);
        if (!reply.getSuccess()) {
            return false;
        }

        TokenInfo basis = verify.getBasis();
        loginInfo.setAppId(basis.getAppId());
        loginInfo.setTenantId(basis.getTenantId());
        loginInfo.setDeptId(basis.getDeptId());
        loginInfo.setUserId(verify.getUserId());
        loginInfo.setUserName(verify.getUserName());

        return true;
    }

    /**
     * 是否被限流
     *
     * @param key   键值
     * @param gap   访问最小时间间隔(秒)
     * @param cycle 限流计时周期(秒)
     * @param max   限制次数/限流周期
     * @return 是否限制访问
     */
    private boolean isLimited(String key, Integer gap, Integer cycle, Integer max) {
        return isLimited(key, gap) || isLimited(key, cycle, max);
    }

    /**
     * 获取限流计时周期剩余秒数
     *
     * @param key 键值
     * @param gap 限流计时周期秒数
     * @return 是否限制访问
     */
    private boolean isLimited(String key, Integer gap) {
        if (key == null || key.isEmpty() || gap == null || gap.equals(0)) {
            return false;
        }

        key = "Surplus:" + key;
        String val = Redis.get(key);
        if (val == null || val.isEmpty()) {
            Redis.set(key, DateHelper.getDateTime(), gap, TimeUnit.SECONDS);

            return false;
        }

        Date time = DateHelper.parseDateTime(val);
        long bypass = System.currentTimeMillis() - Objects.requireNonNull(time).getTime();

        // 调用时间间隔低于1秒时,重置调用时间为当前时间作为惩罚
        if (bypass < 1000) {
            Redis.set(key, DateHelper.getDateTime(), gap, TimeUnit.SECONDS);
        }

        reply = ReplyHelper.tooOften();
        return true;
    }

    /**
     * 是否被限流(超过限流计时周期最大访问次数)
     *
     * @param key   键值
     * @param cycle 限流计时周期(秒)
     * @param max   限制次数/限流周期
     * @return 是否限制访问
     */
    private Boolean isLimited(String key, Integer cycle, Integer max) {
        if (key == null || key.isEmpty() || cycle == null || cycle.equals(0) || max == null || max.equals(0)) {
            return false;
        }

        // 如记录不存在,则记录访问次数为1
        key = "Limit:" + key;
        String val = Redis.get(key);
        if (val == null || val.isEmpty()) {
            Redis.set(key, "1", cycle, TimeUnit.SECONDS);

            return false;
        }

        // 读取访问次数,如次数超过限制,返回true,否则访问次数增加1次
        Integer count = Integer.valueOf(val);
        long expire = Redis.getExpire(key, TimeUnit.SECONDS);
        if (count > max) {
            reply = ReplyHelper.tooOften();
            return true;
        }

        count++;
        Redis.set(key, count.toString(), expire, TimeUnit.SECONDS);

        return false;
    }

    /**
     * 生成返回数据
     *
     * @param exchange ServerWebExchange
     * @return Mono
     */
    private Mono<Void> initResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();

        //设置headers
        HttpHeaders httpHeaders = response.getHeaders();
        httpHeaders.add("Content-Type", "application/json; charset=UTF-8");
        httpHeaders.add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

        //设置body
        String json = Json.toJson(reply);
        DataBuffer bodyDataBuffer = response.bufferFactory().wrap(json.getBytes());

        return response.writeWith(Mono.just(bodyDataBuffer));
    }

    /**
     * 获取鉴权配置
     *
     * @return 鉴权配置信息
     */
    private List<InterfaceConfig> getConfigs() {
        return null;
    }
}

package com.insight.gateway.filter;

import com.insight.gateway.common.Verify;
import com.insight.utils.*;
import com.insight.utils.pojo.InterfaceDto;
import com.insight.utils.pojo.LoginInfo;
import com.insight.utils.pojo.Reply;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author 宣炳刚
 * @date 2019-08-29
 * @remark 身份验证及鉴权过滤器
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private LocalDateTime flagTime = LocalDateTime.now();
    private List<InterfaceDto> regConfigs = new ArrayList<>();
    private Map<String, InterfaceDto> hashConfigs = new HashMap<>();

    /**
     * 令牌持有人信息
     */
    private LoginInfo loginInfo;

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
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();
        InterfaceDto config = getConfig(method, path);
        if (config == null) {
            reply = ReplyHelper.fail("不存在的URL: " + method + ":" + path);
            return initResponse(exchange);
        }

        // 验证及鉴权
        String key = method + ":" + path;
        HttpHeaders headers = request.getHeaders();
        String fingerprint = headers.getFirst("fingerprint");
        Boolean isLogResult = config.getLogResult();
        exchange.getAttributes().put("logResult", isLogResult != null && isLogResult);
        if (!config.getVerify()) {
            return isLimited(config, fingerprint, key) ? initResponse(exchange) : chain.filter(exchange);
        }

        String token = headers.getFirst("Authorization");
        boolean isVerified = verify(token, fingerprint, config.getAuthCode());
        if (!isVerified || isLimited(config, fingerprint, key)) {
            return initResponse(exchange);
        }

        // 验证提交数据临时Token
        if (config.getNeedToken()) {
            String redisKey = "SubmitToken:" + Util.md5(loginInfo.getUserId() + ":" + key);
            String submitToken = headers.getFirst("SubmitToken");
            String id = Redis.get(redisKey);
            if (id == null || id.isEmpty() || !id.equals(submitToken)) {
                reply = ReplyHelper.fail("SubmitToken不存在");
                return initResponse(exchange);
            } else {
                Redis.deleteKey(redisKey);
            }
        }

        request.mutate().header("loginInfo", loginInfo.toString()).build();
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
     * 验证用户令牌并鉴权
     *
     * @param token       令牌
     * @param fingerprint 用户特征串
     * @param authCode    接口授权码
     * @return 是否通过验证
     */
    private boolean verify(String token, String fingerprint, String authCode) {
        if (token == null || token.isEmpty()) {
            reply = ReplyHelper.invalidToken();

            return false;
        }

        Verify verify = new Verify(token, fingerprint);
        reply = verify.compare(authCode);
        if (!reply.getSuccess()) {
            return false;
        }

        loginInfo = Json.clone(verify, LoginInfo.class);
        return true;
    }

    /**
     * 是否被限流
     *
     * @param config      接口配置表
     * @param fingerprint 用户特征串
     * @param key         键值
     * @return 是否被限流
     */
    private boolean isLimited(InterfaceDto config, String fingerprint, String key) {
        if (!config.getLimit() || key == null || key.isEmpty()) {
            return false;
        }

        Integer gap = config.getLimitGap();
        if (gap == null || gap.equals(0)) {
            return false;
        }

        String limitKey = Util.md5(fingerprint + "|" + key);
        if (isLimited(limitKey, gap)) {
            return true;
        }

        Integer cycle = config.getLimitCycle();
        if (cycle == null || cycle.equals(0)) {
            return false;
        }

        Integer max = config.getLimitMax();
        if (max == null || max.equals(0)) {
            return false;
        }

        return isLimited(limitKey, cycle, max, config.getMessage());
    }

    /**
     * 是否被限流(访问间隔小于最小时间间隔)
     *
     * @param key 键值
     * @param gap 访问最小时间间隔
     * @return 是否限制访问
     */
    private boolean isLimited(String key, Integer gap) {
        key = "Surplus:" + key;
        String val = Redis.get(key);
        if (val == null || val.isEmpty()) {
            Redis.set(key, DateHelper.getDateTime(), gap, TimeUnit.SECONDS);
            return false;
        }

        // 调用时间间隔低于1秒时,重置调用时间为当前时间作为惩罚
        LocalDateTime time = LocalDateTime.parse(val).plusSeconds(1);
        if (LocalDateTime.now().isBefore(time)) {
            Redis.set(key, DateHelper.getDateTime(), gap, TimeUnit.SECONDS);
        }

        reply = ReplyHelper.tooOften();
        return true;
    }

    /**
     * 是否被限流(限流计时周期内超过最大访问次数)
     *
     * @param key   键值
     * @param cycle 限流计时周期(秒)
     * @param max   限制次数/限流周期
     * @param msg   消息
     * @return 是否限制访问
     */
    private Boolean isLimited(String key, Integer cycle, Integer max, String msg) {
        key = "Limit:" + key;
        String val = Redis.get(key);
        if (val == null || val.isEmpty()) {
            Redis.set(key, "1", cycle, TimeUnit.SECONDS);

            return false;
        }

        // 读取访问次数,如次数超过限制,返回true,否则访问次数增加1次
        int count = Integer.parseInt(val);
        if (count > max) {
            reply = ReplyHelper.tooOften(msg);
            return true;
        }

        count++;
        long expire = Redis.getExpire(key, TimeUnit.MILLISECONDS);
        Redis.set(key, Integer.toString(count), expire, TimeUnit.MILLISECONDS);

        return false;
    }

    /**
     * 生成返回数据
     *
     * @param exchange ServerWebExchange
     * @return Mono
     */
    private Mono<Void> initResponse(ServerWebExchange exchange) {
        exchange.getAttributes().put("logResult", true);
        ServerHttpResponse response = exchange.getResponse();

        //设置body
        String json = Json.toJson(reply);
        byte[] data = json.getBytes();
        DataBuffer body = response.bufferFactory().wrap(data);

        //设置headers
        HttpHeaders httpHeaders = response.getHeaders();
        httpHeaders.setAccessControlAllowOrigin("*");
        //noinspection deprecation
        httpHeaders.setContentType(MediaType.APPLICATION_JSON_UTF8);
        httpHeaders.setDate(System.currentTimeMillis());
        httpHeaders.setVary(Arrays.asList("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));

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
        // 刷新缓存
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(flagTime.plusSeconds(60))) {
            flagTime = now;
            hashConfigs = getHashConfigs();
            regConfigs = getRegularConfigs();
        }

        // 先进行哈希匹配
        String hash = Util.md5(method.name() + ":" + url);
        if (hashConfigs.containsKey(hash)) {
            return hashConfigs.get(hash);
        }

        // 哈希匹配失败后进行正则匹配
        String path = method + ":" + url;
        for (InterfaceDto config : regConfigs) {
            String regular = config.getRegular();
            if (path.matches(regular)) {
                return config;
            }
        }

        // 重载配置进行哈希匹配
        hashConfigs = getHashConfigs();
        if (hashConfigs.containsKey(hash)) {
            return hashConfigs.get(hash);
        }

        // 重载配置进行正则匹配
        regConfigs = getRegularConfigs();
        for (InterfaceDto config : regConfigs) {
            String regular = config.getRegular();
            if (path.matches(regular)) {
                return config;
            }
        }

        return null;
    }

    /**
     * 获取接口配置哈希表
     *
     * @return 接口配置表
     */
    private Map<String, InterfaceDto> getHashConfigs() {
        String json = Redis.get("Config:Interface");
        List<InterfaceDto> list = Json.toList(json, InterfaceDto.class);
        Map<String, InterfaceDto> map = new HashMap<>(list.size());
        for (InterfaceDto config : list) {
            String url = config.getUrl();
            if (!url.contains("{")) {
                String hash = Util.md5(config.getMethod() + ":" + config.getUrl());
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
        String json = Redis.get("Config:Interface");
        List<InterfaceDto> list = Json.toList(json, InterfaceDto.class);
        for (InterfaceDto config : list) {
            String method = config.getMethod();
            String url = config.getUrl();
            if (url.contains("{")) {
                // 此正则表达式仅支持UUID作为路径参数,如使用其他类型的参数.请修改正则表达式以匹配参数类型
                String regular = method + ":" + url.replaceAll("/\\{[a-zA-Z]+}", "/[0-9a-f]{32}");
                config.setRegular(regular);
            }
        }

        return list.stream().filter(i -> i.getRegular() != null).collect(Collectors.toList());
    }
}

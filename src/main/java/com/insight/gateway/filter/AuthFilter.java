package com.insight.gateway.filter;

import com.insight.gateway.common.InterfaceConfig;
import com.insight.gateway.common.Verify;
import com.insight.util.*;
import com.insight.util.pojo.LoginInfo;
import com.insight.util.pojo.Reply;
import com.insight.util.pojo.TokenInfo;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author 宣炳刚
 * @date 2019-08-29
 * @remark 身份验证及鉴权过滤器
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private List<InterfaceConfig> regConfigs = new ArrayList<>();
    private Map<String, InterfaceConfig> hashConfigs = new HashMap<>();

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
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();
        InterfaceConfig config = getConfig(method, path);
        if (config == null) {
            reply = ReplyHelper.fail("请求的URL不存在");
            return initResponse(exchange);
        }

        // 接口限流
        int limitType = config.getLimitType();
        if (limitType > 0) {
            String key;
            if (limitType == 1) {
                key = path;
            } else if (method == HttpMethod.GET) {
                MultiValueMap<String, String> params = request.getQueryParams();
                key = Json.toJson(params);
            } else {
                Flux<DataBuffer> body = request.getBody();
                key = body.toString();
            }

            if (isLimited(Util.md5(key), config.getLimitGap(), config.getLimitCycle(), config.getLimitMax(), config.getMessage())) {
                return initResponse(exchange);
            }
        }

        // 验证及鉴权
        HttpHeaders headers = request.getHeaders();
        String token = headers.getFirst("Authorization");
        String fingerprint = headers.getFirst("fingerprint");
        if (config.getType() > 0 && verify(token, fingerprint, config.getAuthCode())) {
            return initResponse(exchange);
        }

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
        return 2;
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
     * @param msg   消息
     * @return 是否限制访问
     */
    private boolean isLimited(String key, Integer gap, Integer cycle, Integer max, String msg) {
        return isLimited(key, gap, msg) || isLimited(key, cycle, max, msg);
    }

    /**
     * 是否被限流(访问间隔小于最小时间间隔)
     *
     * @param key 键值
     * @param gap 访问最小时间间隔
     * @param msg 消息
     * @return 是否限制访问
     */
    private boolean isLimited(String key, Integer gap, String msg) {
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

        reply = ReplyHelper.tooOften(msg);
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
            reply = ReplyHelper.tooOften(msg);
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
        DataBuffer body = response.bufferFactory().wrap(json.getBytes());

        return response.writeWith(Mono.just(body));
    }

    /**
     * 通过匹配URL获取接口配置
     *
     * @param method 请求方法
     * @param url    请求URL
     * @return 接口配置
     */
    private InterfaceConfig getConfig(HttpMethod method, String url) {
        // 先进行哈希匹配
        String hash = Util.md5(method.name() + ":" + url);
        if (hashConfigs.containsKey(hash)) {
            return hashConfigs.get(hash);
        }

        // 哈希匹配失败后进行正则匹配
        for (InterfaceConfig config : regConfigs) {
            String regular = config.getRegular();
            if (Pattern.compile(regular).matcher(url).matches()) {
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
        for (InterfaceConfig config : regConfigs) {
            String regular = config.getRegular();
            if (Pattern.compile(regular).matcher(url).matches()) {
                return config;
            }
        }

        return null;
    }

    /**
     * 获取接口配置正则表
     *
     * @return 接口配置表
     */
    private List<InterfaceConfig> getRegularConfigs() {
        String json = Redis.get("Config:Interface");
        List<InterfaceConfig> list = Json.toList(json, InterfaceConfig.class);
        for (InterfaceConfig config : list) {
            String url = config.getUrl();
            if (url.contains("{")) {
                config.setRegular("");
            }
        }

        return list.stream().filter(i -> i.getRegular() != null).collect(Collectors.toList());
    }

    /**
     * 获取接口配置哈希表
     *
     * @return 接口配置表
     */
    private Map<String, InterfaceConfig> getHashConfigs() {
        String json = Redis.get("Config:Interface");
        List<InterfaceConfig> list = Json.toList(json, InterfaceConfig.class);
        Map<String, InterfaceConfig> map = new HashMap<>(list.size());
        for (InterfaceConfig config : list) {
            String url = config.getUrl();
            if (!url.contains("{")) {
                String hash = Util.md5(config.getMethod() + ":" + config.getUrl());
                map.put(hash, config);
            }
        }

        return map;
    }
}

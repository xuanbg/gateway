package com.insight.gateway.filter;

import com.insight.gateway.common.Log;
import com.insight.util.Generator;
import com.insight.util.Json;
import com.insight.util.Util;
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
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * @author 宣炳刚
 * @date 2017/10/06
 * @remark 调试信息过滤器
 */
@Component
public class LogFilter implements GlobalFilter, Ordered {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 请求信息日志过滤器
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @return Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Log log = new Log();
        log.setTime(new Date());
        log.setLevel("INFO");

        // 读取客户端IP地址、请求方法和调用的接口URL
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        HttpMethod method = request.getMethod();
        RequestPath path = request.getPath();
        String source = getIp(headers);
        if (source == null || source.isEmpty()) {
            source = request.getRemoteAddress().getAddress().getHostAddress();
        }

        String requestId = Generator.uuid();
        String fingerprint = Util.md5(source + headers.getFirst("user-agent"));
        request.mutate().header("fingerprint", fingerprint).build();
        request.mutate().header("requestId", requestId).build();

        log.setRequestId(requestId);
        log.setSource(source);
        log.setMethod(method.name());
        log.setUrl(path.value());
        log.setHeaders(headers.toSingleValueMap());

        // 读取请求参数
        MultiValueMap<String, String> params = request.getQueryParams();
        log.setParams(params.isEmpty() ? null : params.toSingleValueMap());

        // 如请求方法为GET,则打印日志后结束
        if (method.matches("GET")) {
            logger.info("请求参数: {}", Json.toJson(log));

            return chain.filter(exchange);
        }

        Flux<DataBuffer> body = request.getBody();
        boolean isMatch = Pattern.matches("^[{|\\[].*[}|\\]]$", body.toString());
        if (isMatch) {
            Map bodyMap = Json.toMap(body.toString());
            log.setBody(bodyMap == null ? body.toString() : bodyMap);
        } else {
            log.setBody(body.toString());
        }

        logger.info("请求参数：{}", Json.toJson(log));
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
     * 获取客户端IP
     *
     * @param headers 请求头
     * @return 客户端IP
     */
    private String getIp(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return null;
        }

        AtomicReference<String> ip = new AtomicReference<>(headers.getFirst("X-Real-IP"));
        if (ip.get() == null || ip.get().isEmpty()) {
            ip.set(headers.getFirst("X-Forwarded-For"));
        }

        if (ip.get() == null || ip.get().isEmpty()) {
            ip.set(headers.getFirst("Proxy-Client-IP"));
        }

        if (ip.get() == null || ip.get().isEmpty()) {
            ip.set(headers.getFirst("WL-Proxy-Client-IP"));
        }

        return ip.get();
    }
}

package com.insight.gateway.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * @author 宣炳刚
 * @date 2023/6/1
 * @remark
 */

@Order(-100)
@Configuration
public class GlobalCorsFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var response = exchange.getResponse();
        var headers = response.getHeaders();
        var origin = request.getHeaders().getFirst("Origin");

        if (origin != null) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Access-Control-Allow-Credentials", "true");
            headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            headers.set("Access-Control-Allow-Headers", "*");
            headers.set("Access-Control-Max-Age", "3600");
            headers.set("Vary", "Origin");
        }

        // OPTIONS预检直接返回200，不往下走鉴权逻辑
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            response.setStatusCode(HttpStatus.OK);
            return response.setComplete();
        }

        return chain.filter(exchange);
    }
}

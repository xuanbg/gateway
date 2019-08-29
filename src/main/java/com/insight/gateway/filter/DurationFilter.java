package com.insight.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author 宣炳刚
 * @date 2017/10/06
 * @remark 调试信息过滤器
 */
@Component
public class DurationFilter implements GlobalFilter, Ordered {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String COUNT_START_TIME = "StartTime";

    /**
     * 处理时间统计过滤器
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @return Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(COUNT_START_TIME, System.currentTimeMillis());
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Long startTime = exchange.getAttribute(COUNT_START_TIME);
            if (startTime == null){
                return;
            }

            String body = exchange.getResponse().bufferFactory().toString();
            logger.info("返回数据: " + body);

            long duration = (System.currentTimeMillis() - startTime);
            logger.info("时长: " + duration + " ms");
        }));
    }

    /**
     * 获取过滤器序号
     *
     * @return 过滤器序号
     */
    @Override
    public int getOrder() {
        return 0;
    }
}

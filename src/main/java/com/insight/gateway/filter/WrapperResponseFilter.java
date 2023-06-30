package com.insight.gateway.filter;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 宣炳刚
 * @date 2019-08-30
 * @remark
 */
@Component
public class WrapperResponseFilter implements GlobalFilter, Ordered {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 响应消息输出过滤器
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @return Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var originalResponse = exchange.getResponse();
        var responseDecorator = new ServerHttpResponseDecorator(originalResponse) {

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                String requestId = exchange.getAttribute("requestId");
                Boolean logResult = exchange.getAttribute("logResult");
                if (body instanceof Flux && logResult != null && logResult) {
                    var fluxBody = Flux.from(body);

                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        List<String> list = new ArrayList<>();
                        dataBuffers.forEach(dataBuffer -> {
                            var content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);
                            list.add(new String(content, StandardCharsets.UTF_8));
                        });

                        var json = String.join("", list);
                        logger.info("requestId: {}. 返回数据: {}", requestId, json);

                        return bufferFactory().wrap(json.getBytes());
                    }));
                }

                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    /**
     * 获取过滤器序号
     *
     * @return 过滤器序号
     */
    @Override
    public int getOrder() {
        return -2;
    }
}
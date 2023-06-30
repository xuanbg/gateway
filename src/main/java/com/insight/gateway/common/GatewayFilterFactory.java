package com.insight.gateway.common;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author 宣炳刚
 * @date 2023/6/30
 * @remark
 */

@Component
public class GatewayFilterFactory extends AbstractGatewayFilterFactory<GatewayFilterFactory.Config> {

    //此处需要定义List<HttpMessageReader<?>>
    private final List<HttpMessageReader<?>> messageReaders;

    //构造方法
    public GatewayFilterFactory(ServerCodecConfigurer serverCodecConfigurer) {
        super(GatewayFilterFactory.Config.class);
        //将messageReaders通过传进来的serverCodecConfigurer赋值。
        this.messageReaders = serverCodecConfigurer.getReaders();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new MyGatewayFilter(config);
    }

    public class MyGatewayFilter implements GatewayFilter, Ordered {

        private final Config config;

        public MyGatewayFilter(Config config) {
            this.config = config;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (!config.isEnable()) {
                return chain.filter(exchange);
            }

            var serverRequest = ServerRequest.create(exchange, messageReaders);
            var serverHttpRequest = exchange.getRequest();
            var mediaType = serverHttpRequest.getHeaders().getContentType();
            var modifyBody = serverRequest.bodyToMono(String.class).flatMap(o -> MediaType.APPLICATION_JSON.isCompatibleWith(mediaType) ? Mono.justOrEmpty(o) : Mono.empty());

            var bodyInserter = BodyInserters.fromPublisher(modifyBody, String.class);
            var headers = new HttpHeaders();
            headers.putAll(exchange.getRequest().getHeaders());
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.set(HttpHeaders.CONTENT_TYPE, serverHttpRequest.getHeaders().getFirst("Content-Type"));
            var outputMessage = new CachedBodyOutputMessage(exchange, headers);
            return bodyInserter.insert(outputMessage, new BodyInserterContext())
                    .then(Mono.defer(() -> {
                        var requestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public HttpHeaders getHeaders() {
                                var contentLength = headers.getContentLength();
                                var httpHeaders = new HttpHeaders();
                                httpHeaders.putAll(super.getHeaders());
                                if (contentLength > 0) {
                                    httpHeaders.setContentLength(contentLength);
                                } else {
                                    httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                                }
                                return httpHeaders;
                            }

                            @Override
                            public Flux<DataBuffer> getBody() {
                                return outputMessage.getBody();
                            }
                        };

                        return chain.filter(exchange.mutate().request(requestDecorator).build());
                    }));
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    public static class Config {
        private boolean enable;

        public Config() {

        }

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }
    }
}

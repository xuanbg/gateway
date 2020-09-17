package com.insight.gateway.filter;

import com.insight.gateway.common.dto.LogDto;
import com.insight.utils.Json;
import com.insight.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
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
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String source = getIp(headers);
        if (source == null || source.isEmpty()) {
            source = request.getRemoteAddress().getAddress().getHostAddress();
        }

        String requestId = Util.uuid();
        String fingerprint = Util.md5(source + headers.getFirst("user-agent"));
        request.mutate().header("requestId", requestId).build();
        request.mutate().header("fingerprint", fingerprint).build();
        exchange.getAttributes().put("requestId", requestId);

        // 构造入参对象
        HttpMethod method = request.getMethod();
        RequestPath path = request.getPath();
        LogDto log = new LogDto();
        log.setSource(source);
        log.setMethod(method.name());
        log.setUrl(path.value());
        log.setHeaders(headers.toSingleValueMap());

        // 读取请求参数
        MultiValueMap<String, String> params = request.getQueryParams();
        log.setParams(params.isEmpty() ? null : params.toSingleValueMap());

        // 如请求方法为GET或Body为空或Body不是Json,则打印日志后结束
        long length = headers.getContentLength();
        MediaType contentType = headers.getContentType();
        if (length <= 0 || !contentType.equalsTypeAndSubtype(MediaType.APPLICATION_JSON)) {
            logger.info("requestId: {}. 请求参数: {}", requestId, log.toString());

            return chain.filter(exchange);
        }

        return readBody(exchange, chain, log);
    }

    /**
     * 输出请求体
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @param log      日志DTO
     * @return Mono
     */
    private Mono<Void> readBody(ServerWebExchange exchange, GatewayFilterChain chain, LogDto log) {
        Flux<DataBuffer> dataBufferFlux = exchange.getRequest().getBody();
        return DataBufferUtils.join(dataBufferFlux).flatMap(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);

            // 重新构造请求
            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return Flux.defer(() -> {
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                        DataBufferUtils.retain(buffer);

                        return Flux.just(buffer);
                    });
                }
            };

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return ServerRequest.create(mutatedExchange, HandlerStrategies.withDefaults().messageReaders()).bodyToMono(String.class).doOnNext(body -> {
                if (Pattern.matches("^\\[.*]$", body)) {
                    List<Object> list = Json.toList(body, Object.class);
                    log.setBody(list == null ? body : list);
                } else if (Pattern.matches("^\\{.*}$", body)) {
                    Map obj = Json.toMap(body);
                    log.setBody(obj == null ? body : obj);
                } else {
                    log.setBody(body);
                }

                String requestId = exchange.getAttribute("requestId");
                logger.info("requestId: {}. 请求参数：{}", requestId, log.toString());
            }).then(chain.filter(mutatedExchange));
        });
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

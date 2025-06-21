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
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author 宣炳刚
 * @date 2017/10/06
 * @remark 调试信息过滤器
 */
@Component
public class LogFilter implements GlobalFilter, Ordered {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final List<String> allowHeaders = Arrays.asList("Accept", "Accept-Encoding", "Authorization", "Content-Type", "Host", "fingerprint", "token", "key", "User-Agent");
    private final ServerCodecConfigurer config;

    public LogFilter(ServerCodecConfigurer config) {
        this.config = config;
    }

    /**
     * 请求信息日志过滤器
     *
     * @param exchange ServerWebExchange
     * @param chain    GatewayFilterChain
     * @return Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var headers = request.getHeaders();
        var source = getIp(headers);
        if (source == null || source.isEmpty()) {
            source = request.getRemoteAddress().getAddress().getHostAddress();
        }

        var token = headers.getFirst("Authorization");
        var userAgent = headers.getFirst("User-Agent");
        var path = request.getPath();
        var requestId = Util.uuid();
        var fingerprint = Util.md5(source + userAgent + token);
        exchange.getAttributes().put("requestId", requestId);
        request.mutate().header("requestId", requestId).build();
        request.mutate().header("fingerprint", fingerprint).build();

        // 处理请求头
        var headerMap = headers.toSingleValueMap().entrySet().stream()
                .filter(e -> allowHeaders.stream().anyMatch(i -> i.equalsIgnoreCase(e.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 构造入参对象
        var method = request.getMethod();
        var log = new LogDto();
        log.setSource(source);
        log.setMethod(method.name());
        log.setUrl(path.value());
        log.setHeaders(headerMap);

        // 读取请求参数
        var params = request.getQueryParams();
        log.setParams(params.isEmpty() ? null : params.toSingleValueMap());

        // 如Body不为空,则将body内容加入日志
        var length = headers.getContentLength();
        if (length > 0) {
            return readBody(exchange, chain, log);
        }

        if (Util.isEmpty(token)) {
            logger.info("requestId: {}. 请求参数: {}", requestId, log);
        } else {
            var appId = Json.toToken(token).getAppId();
            logger.info("requestId: {}. AppId: {}. 请求参数: {}", requestId, appId, log);
        }

        return chain.filter(exchange);
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
        var dataBufferFlux = exchange.getRequest().getBody();
        return DataBufferUtils.join(dataBufferFlux).flatMap(dataBuffer -> {
            var bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);

            // 重新构造请求
            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return Flux.defer(() -> {
                        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                        DataBufferUtils.retain(buffer);

                        return Flux.just(buffer);
                    });
                }
            };

            var mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return ServerRequest.create(mutatedExchange, config.getReaders()).bodyToMono(String.class).doOnNext(body -> {
                String requestId = exchange.getAttribute("requestId");
                if (Pattern.matches("^\\[.*]$", body)) {
                    var list = Json.toList(body, Object.class);
                    log.setBody(list == null ? body : list);
                } else if (Pattern.matches("^\\{.*}$", body)) {
                    Map obj = Json.toMap(body);
                    log.setBody(obj == null ? body : obj);
                } else {
                    log.setBody(body);
                }

                log.setBodyLength(body.length());
                logger.info("requestId: {}. 请求参数：{}", requestId, log);
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

        var ip = headers.getFirst("WL-Proxy-Client-IP");
        if (Util.isNotEmpty(ip)) {
            return ip;
        }

        ip = headers.getFirst("X-Forwarded-For");
        if (Util.isNotEmpty(ip)) {
            return ip;
        }

        ip = headers.getFirst("X-Real-IP");
        if (Util.isNotEmpty(ip)) {
            return ip;
        }

        return null;
    }
}

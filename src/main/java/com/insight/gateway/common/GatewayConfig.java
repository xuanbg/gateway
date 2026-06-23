package com.insight.gateway.common;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 宣炳刚
 * @date 2026/6/23
 * @remark 网关配置
 */
@Configuration
public class GatewayConfig {

    /**
     * 创建路由
     *
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("common-basedata", r -> r.path("/common/config/**,/common/param/**,/common/report/**,/common/area/**,/common/dict/**,/common/log/**,/common/file/**").uri("lb://common-basedata"))
                .route("common-message", r -> r.path("/common/message/**").uri("lb://common-message"))
                .route("base-auth", r -> r.path("/base/auth/**").uri("lb://base-auth"))
                .route("base-role", r -> r.path("/base/role/**").uri("lb://base-role"))
                .route("base-organize", r -> r.path("/base/organize/**").uri("lb://base-organize"))
                .route("base-tenant", r -> r.path("/base/tenant/**").uri("lb://base-tenant"))
                .route("base-user", r -> r.path("/base/user/**").uri("lb://base-user"))
                .route("base-resource", r -> r.path("/base/resource/**").uri("lb://base-resource"))
                .route("hxb-ai", r -> r.path("/hxb/ai/**").uri("lb://hxb-ai"))
                .route("hxb-basedata", r -> r.path("/basedata/**").uri("lb://hxb-basedata"))
                .route("hxb-resource", r -> r.path("/resource/**").uri("lb://hxb-resource"))
                .route("hxb-prepare", r -> r.path("/hxb/plan/**,/hxb/teach/**").uri("lb://hxb-prepare"))
                .route("hxb-research", r -> r.path("/hxb/research/**").uri("lb://hxb-research"))
                .route("hxb-study", r -> r.path("/hxb/study/**").uri("lb://hxb-study"))
                .route("hxb-tutor", r -> r.path("/hxb/tutor/**,/hxb/note/**,/hxb/question/**").uri("lb://hxb-tutor"))
                .route("hxb-homework", r -> r.path("/hxb/homework/**").uri("lb://hxb-homework"))
                .route("hxb-classwork", r -> r.path("/hxb/classwork/**").uri("lb://hxb-classwork"))
                .route("hxb-classtest", r -> r.path("/hxb/classtest/**").uri("lb://hxb-classtest"))
                .route("hxb-promote", r -> r.path("/hxb/promote/**,/hxb/stats/**").uri("lb://hxb-promote"))
                .route("hxb-credit", r -> r.path("/hxb/credit/**").uri("lb://hxb-credit"))
                .route("hxb-contest", r -> r.path("/contest/**").uri("lb://hxb-contest"))
                .route("hxb-statistical", r -> r.path("/statistical/**").uri("lb://hxb-statistical"))
                .build();
    }
}

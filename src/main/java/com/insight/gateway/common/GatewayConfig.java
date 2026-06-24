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
                .route("common-config", r -> r.path("/common/config/**").uri("lb://common-basedata"))
                .route("common-param", r -> r.path("/common/param/**").uri("lb://common-basedata"))
                .route("common-report", r -> r.path("/common/report/**").uri("lb://common-basedata"))
                .route("common-area", r -> r.path("/common/area/**").uri("lb://common-basedata"))
                .route("common-dict", r -> r.path("/common/dict/**").uri("lb://common-basedata"))
                .route("common-log", r -> r.path("/common/log/**").uri("lb://common-basedata"))
                .route("common-file", r -> r.path("/common/file/**").uri("lb://common-basedata"))
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
                .route("hxb-plan", r -> r.path("/hxb/plan/**").uri("lb://hxb-prepare"))
                .route("hxb-teach", r -> r.path("/hxb/teach/**").uri("lb://hxb-prepare"))
                .route("hxb-research", r -> r.path("/hxb/research/**").uri("lb://hxb-research"))
                .route("hxb-study", r -> r.path("/hxb/study/**").uri("lb://hxb-study"))
                .route("hxb-tutor", r -> r.path("/hxb/tutor/**").uri("lb://hxb-tutor"))
                .route("hxb-note", r -> r.path("/hxb/note/**").uri("lb://hxb-tutor"))
                .route("hxb-question", r -> r.path("/hxb/question/**").uri("lb://hxb-tutor"))
                .route("hxb-homework", r -> r.path("/hxb/homework/**").uri("lb://hxb-homework"))
                .route("hxb-classwork", r -> r.path("/hxb/classwork/**").uri("lb://hxb-classwork"))
                .route("hxb-classtest", r -> r.path("/hxb/classtest/**").uri("lb://hxb-classtest"))
                .route("hxb-promote", r -> r.path("/hxb/promote/**").uri("lb://hxb-promote"))
                .route("hxb-stats", r -> r.path("/hxb/stats/**").uri("lb://hxb-promote"))
                .route("hxb-credit", r -> r.path("/hxb/credit/**").uri("lb://hxb-credit"))
                .route("hxb-contest", r -> r.path("/contest/**").uri("lb://hxb-contest"))
                .route("hxb-statistical", r -> r.path("/statistical/**").uri("lb://hxb-statistical"))
                .build();
    }
}

package com.insight.gateway.common;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author 宣炳刚
 * @date 2023-1-4
 * @remark Feign配置类
 */
@Configuration
public class FeignClientConfig implements RequestInterceptor {
    private static final String REGULAR = "fingerprint|requestid|logininfo";

    /**
     * 应用配置
     *
     * @param template RequestTemplate
     */
    @Override
    public void apply(RequestTemplate template) {
        var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        var request = requestAttributes.getRequest();
        var headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            var name = headers.nextElement();
            if (name.toLowerCase().matches(REGULAR)) {
                var values = request.getHeader(name);
                template.header(name, values);
            }
        }
    }
}

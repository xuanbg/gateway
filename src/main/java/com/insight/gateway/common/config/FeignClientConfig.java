package com.insight.gateway.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

/**
 * @author 宣炳刚
 * @date 2019-09-09
 * @remark Feign配置类
 */
@Configuration
public class FeignClientConfig implements RequestInterceptor {
    private static final String REGULAR = "fingerprint|requestId|loginInfo";

    /**
     * 应用配置
     *
     * @param template RequestTemplate
     */
    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        HttpServletRequest request = requestAttributes.getRequest();
        Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            String name = headers.nextElement();
            if (name.matches(REGULAR)) {
                String values = request.getHeader(name);
                template.header(name, values);
            }
        }
    }
}

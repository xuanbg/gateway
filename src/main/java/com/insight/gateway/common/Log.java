package com.insight.gateway.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author 宣炳刚
 * @date 2017/11/4
 * @remark 日志记录类
 */
public class Log implements Serializable {
    private static final long serialVersionUID = -1L;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 日志时间
     */
    private LocalDateTime time;

    /**
     * 日志级别(DEBUG,INFO,WARN,ERROR)
     */
    private String level;

    /**
     * 来源IP
     */
    private String source;

    /**
     * 请求方法(GET,POST,PUT,DELETE,OPTION)
     */
    private String method;

    /**
     * 目标接口URL
     */
    private String url;

    /**
     * 请求头信息
     */
    private Map<String, String> headers;

    /**
     * 请求参数
     */
    private Map<String, String> params;

    /**
     * 请求体数据
     */
    private Object body;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
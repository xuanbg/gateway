package com.insight.gateway.common;

import com.insight.gateway.common.client.AuthClient;
import com.insight.utils.Json;
import com.insight.utils.pojo.Reply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 宣炳刚
 * @date 2020/6/26
 * @remark
 */
@Component
public class Core {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final AuthClient client;

    /**
     * 构造方法
     *
     * @param client AuthClient
     */
    public Core(AuthClient client) {
        this.client = client;
    }

    /**
     * 获取用户权限集合
     *
     * @param requestId 请求ID
     * @param info      用户关键信息
     * @return 权限集合
     */
    public List<String> getPermits(String requestId, String info) {
        try {
            Reply reply = client.getPermits(info);
            if (reply.getSuccess()) {
                return Json.cloneList(reply.getData(), String.class);
            } else {
                logger.error("requestId: {}. 错误信息: {}", requestId, reply.getMessage());
                return new ArrayList<>();
            }
        } catch (Exception ex) {
            logger.error("requestId: {}. 错误信息: {}", requestId, ex.getMessage());
            return new ArrayList<>();
        }
    }
}

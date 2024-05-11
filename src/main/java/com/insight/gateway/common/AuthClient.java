package com.insight.gateway.common;

import com.insight.utils.pojo.base.Reply;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author 宣炳刚
 * @date 2019-08-31
 * @remark 消息中心Feign客户端
 */
@FeignClient(name = "base-auth", configuration = FeignClientConfig.class)
public interface AuthClient {

    /**
     * 获取用户授权码
     *
     * @return Reply
     */
    @GetMapping("/base/auth/v1.0/tokens/permits")
    Reply getAuthCodes();
}

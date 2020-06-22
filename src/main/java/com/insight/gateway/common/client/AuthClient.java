package com.insight.gateway.common.client;

import com.insight.utils.pojo.Reply;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * @author 宣炳刚
 * @date 2019-08-31
 * @remark 消息中心Feign客户端
 */
@FeignClient(name = "base-auth")
public interface AuthClient {

    /**
     * 获取用户授权码
     *
     * @param info 用户关键信息
     * @return Reply
     */
    @GetMapping("/base/auth/v1.0/tokens/permits")
    Reply getPermits(@RequestHeader("loginInfo") String info);
}

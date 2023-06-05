package com.insight.gateway.common;

import com.insight.utils.Util;
import com.insight.utils.pojo.base.Reply;

/**
 * @author 作者
 * @date 2017年9月5日
 * @remark Reply帮助类
 */
public final class ReplyHelper {

    /**
     * 未授权
     */
    private static final Reply NO_AUTH_REPLY = new Reply();

    /**
     * 非法Token
     */
    private static final Reply INVALID_TOKEN_REPLY = new Reply();

    /**
     * 未授权
     */
    private static final Reply FORBID_REPLY = new Reply();

    /**
     * 访问过于频繁
     */
    private static final Reply TOO_OFTEN_REPLY = new Reply();

    /**
     * Token过期
     */
    private static final Reply EXPIRED_TOKEN_REPLY = new Reply();

    static {
        //
        NO_AUTH_REPLY.setCode(403);
        NO_AUTH_REPLY.setMessage("未授权");

        //forbidden
        FORBID_REPLY.setCode(413);
        FORBID_REPLY.setMessage("账户被禁止使用");

        //invalid token
        INVALID_TOKEN_REPLY.setCode(421);
        INVALID_TOKEN_REPLY.setMessage("无效凭证");

        //expired token
        EXPIRED_TOKEN_REPLY.setCode(422);
        EXPIRED_TOKEN_REPLY.setMessage("凭证过期，需刷新");

        //请求过于频繁
        TOO_OFTEN_REPLY.setCode(490);
        TOO_OFTEN_REPLY.setMessage("您请求过于频繁，请稍后重试！");
    }

    /**
     * 请求成功
     *
     * @return Reply
     */
    public static Reply success() {
        Reply reply = new Reply();
        reply.setCode(200);
        reply.setMessage("请求成功");

        return reply;
    }

    /**
     * 服务端错误
     *
     * @param requestId 请求ID
     * @param msg       消息
     * @return Reply
     */
    public static Reply fail(String requestId, String msg) {
        Reply reply = new Reply();
        reply.setCode(400);
        reply.setMessage(msg);
        reply.setOption(requestId);

        return reply;
    }

    /**
     * 未授权
     *
     * @param requestId 请求ID
     * @return Reply
     */
    public static Reply noAuth(String requestId) {
        NO_AUTH_REPLY.setOption(requestId);
        return NO_AUTH_REPLY;
    }

    /**
     * 禁止访问
     *
     * @param requestId 请求ID
     * @return Reply
     */
    public static Reply forbid(String requestId) {
        FORBID_REPLY.setOption(requestId);
        return FORBID_REPLY;
    }

    /**
     * 非法token
     *
     * @param requestId 请求ID
     * @return Reply
     */
    public static Reply invalidToken(String requestId) {
        INVALID_TOKEN_REPLY.setOption(requestId);
        return INVALID_TOKEN_REPLY;
    }

    /**
     * token过期
     *
     * @param requestId 请求ID
     * @return Reply
     */
    public static Reply expiredToken(String requestId) {
        EXPIRED_TOKEN_REPLY.setOption(requestId);
        return EXPIRED_TOKEN_REPLY;
    }

    /**
     * 请求过于频繁
     *
     * @param requestId 请求ID
     * @return Reply
     */
    public static Reply tooOften(String requestId) {
        TOO_OFTEN_REPLY.setOption(requestId);
        return TOO_OFTEN_REPLY;
    }

    /**
     * 请求过于频繁
     *
     * @param requestId 请求ID
     * @param msg       错误消息
     * @return Reply
     */
    public static Reply tooOften(String requestId, String msg) {
        if (Util.isNotEmpty(msg)) {
            TOO_OFTEN_REPLY.setMessage(msg);
        }

        TOO_OFTEN_REPLY.setOption(requestId);
        return TOO_OFTEN_REPLY;
    }
}

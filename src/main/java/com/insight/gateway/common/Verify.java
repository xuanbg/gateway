package com.insight.gateway.common;

import com.insight.utils.Json;
import com.insight.utils.Util;
import com.insight.utils.pojo.auth.LoginInfo;
import com.insight.utils.pojo.auth.TokenData;
import com.insight.utils.pojo.auth.TokenKey;
import com.insight.utils.pojo.base.Reply;
import com.insight.utils.pojo.user.User;
import com.insight.utils.redis.HashOps;
import com.insight.utils.redis.StringOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * @author 宣炳刚
 * @date 2019/05/20
 * @remark 用户身份验证类
 */
public class Verify {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String requestId;
    private final TokenKey tokenKey;

    /**
     * 令牌安全码
     */
    private String secret;

    /**
     * 缓存中的令牌信息
     */
    private TokenData basis;

    /**
     * 缓存中的用户信息
     */
    private User user;

    /**
     * 构造方法
     *
     * @param requestId   请求ID
     * @param token       访问令牌
     * @param fingerprint 用户特征串
     */
    public Verify(String requestId, String token, String fingerprint) {
        this.requestId = requestId;

        tokenKey = Json.toToken(token);
        if (tokenKey == null) {
            logger.error("requestId: {}. 错误信息: {}", requestId, "提取验证信息失败。Token is:" + token);
            return;
        }

        secret = tokenKey.getSecret();
        basis = StringOps.get(tokenKey.getKey(), TokenData.class);
        if (basis == null) {
            return;
        }

        if (!basis.getAutoRefresh()) {
            return;
        }

        // 如果Token失效或过期时间大于一半,则不更新过期时间和失效时间.
        if (basis.isHalfLife()) {
            var expire = TokenData.TIME_OUT + basis.getLife();
            basis.setExpiryTime(LocalDateTime.now().plusSeconds(expire));
            StringOps.set(tokenKey.getKey(), basis, expire);
        }
    }

    /**
     * 验证Token合法性
     *
     * @param authCode 接口授权码
     * @return Reply Token验证结果
     */
    public Reply compare(String authCode) {
        if (basis == null) {
            return ReplyHelper.invalidToken(requestId);
        }

        // 验证令牌
        if (!basis.verify(secret)) {
            return ReplyHelper.invalidToken(requestId);
        }

        if (basis.isExpiry()) {
            return ReplyHelper.expiredToken(requestId);
        }

        // 验证用户
        if (invalid()) {
            return ReplyHelper.forbid(requestId);
        }

        // 无需鉴权,返回成功
        if (authCode == null || authCode.isEmpty()) {
            return ReplyHelper.success();
        }

        // 进行鉴权,返回鉴权结果
        if (isPermit(authCode)) {
            return ReplyHelper.success();
        }

        var account = user.getAccount();
        logger.warn("requestId: {}. 告警信息: {}", requestId, "用户『" + account + "』试图使用未授权的功能:" + authCode);

        return ReplyHelper.noAuth(requestId);
    }

    /**
     * 获取令牌持有人的登录信息
     *
     * @return 用户登录信息
     */
    public LoginInfo getLoinInfo() {
        var loginInfo = HashOps.entries("User:" + tokenKey.getUserId(), LoginInfo.class);

        loginInfo.setAppId(basis.getAppId());
        loginInfo.setTenantId(basis.getTenantId());
        loginInfo.setTenantName(basis.getTenantName());
        loginInfo.setOrgId(basis.getOrgId());
        loginInfo.setOrgName(basis.getOrgName());
        loginInfo.setAreaCode(basis.getAreaCode());
        return loginInfo;
    }

    /**
     * 用户是否被禁用
     *
     * @return 是否被禁用
     */
    private boolean invalid() {
        var value = HashOps.get("User:" + tokenKey.getUserId(), "invalid");
        if (value == null) {
            return false;
        }

        return Boolean.parseBoolean(value);
    }

    /**
     * 指定的功能是否授权给用户
     *
     * @param authCode 接口授权码
     * @return 功能是否授权给用户
     */
    private Boolean isPermit(String authCode) {
//        if (basis.isPermitExpiry()) {
//            var reply = client.getAuthCodes();
//            basis.setPermitFuncs(reply.getListFromData());
//            basis.setPermitTime(LocalDateTime.now());
//
//            var expire = DateTime.getRemainSeconds(basis.getExpiryTime());
//            StringOps.set(tokenKey.getKey(), basis, expire);
//        }

        var permits = basis.getPermitFuncs();
        return Util.isNotEmpty(permits) && permits.stream().anyMatch(authCode::equalsIgnoreCase);
    }
}

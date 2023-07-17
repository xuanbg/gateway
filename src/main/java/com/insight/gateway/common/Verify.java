package com.insight.gateway.common;

import com.insight.utils.Json;
import com.insight.utils.Util;
import com.insight.utils.pojo.auth.LoginInfo;
import com.insight.utils.pojo.auth.TokenData;
import com.insight.utils.pojo.base.Reply;
import com.insight.utils.pojo.user.User;
import com.insight.utils.redis.Redis;
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

    /**
     * 令牌哈希值
     */
    private final String hash;

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
     * 令牌ID
     */
    private String tokenId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 构造方法
     *
     * @param requestId   请求ID
     * @param token       访问令牌
     * @param fingerprint 用户特征串
     */
    public Verify(String requestId, String token, String fingerprint) {
        this.requestId = requestId;
        hash = Util.md5(token + fingerprint);
        var accessToken = Json.toToken(token);
        if (accessToken == null) {
            logger.error("requestId: {}. 错误信息: {}", requestId, "提取验证信息失败。Token is:" + token);
            return;
        }

        secret = accessToken.getSecret();
        tokenId = accessToken.getId();
        basis = getToken();
        if (basis == null) {
            return;
        }

        userId = basis.getUserId();
        var map = Redis.getEntity("User:" + userId);
        user = Json.clone(map, User.class);
        if (!basis.getAutoRefresh()) {
            return;
        }

        // 如果Token失效或过期时间大于一半,则不更新过期时间和失效时间.
        if (!basis.isHalfLife()) {
            return;
        }

        var timeOut = TokenData.TIME_OUT;
        long life = basis.getLife();
        var now = LocalDateTime.now();
        basis.setExpiryTime(now.plusSeconds(timeOut + life));

        var expire = timeOut + life;
        Redis.set("Token:" + tokenId, basis.toString(), expire);
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

        // 验证用户
        if (invalid()) {
            return ReplyHelper.forbid(requestId);
        }

        if (loginElsewhere()) {
            return ReplyHelper.fail(requestId, "您的账号已在其他设备登录");
        }

        // 验证令牌
        if (basis.getVerifySource()) {
            if (!basis.verifyTokenHash(hash)) {
                return ReplyHelper.invalidToken(requestId);
            }
        } else {
            if (!basis.verifySecretKey(secret)) {
                return ReplyHelper.invalidToken(requestId);
            }
        }

        if (basis.isExpiry()) {
            return ReplyHelper.expiredToken(requestId);
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
        var data = Redis.getEntity("User:" + userId);
        var loginInfo = Json.clone(data, LoginInfo.class);

        loginInfo.setAppId(basis.getAppId());
        loginInfo.setTenantId(basis.getTenantId());
        loginInfo.setTenantName(basis.getTenantName());
        loginInfo.setOrgId(basis.getOrgId());
        loginInfo.setOrgName(basis.getOrgName());
        loginInfo.setAreaCode(basis.getAreaCode());
        return loginInfo;
    }

    /**
     * 获取令牌中的用户ID
     *
     * @return 是否同一用户
     */
    private boolean userIsEquals(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 根据令牌ID获取缓存中的Token
     *
     * @return TokenInfo(可能为null)
     */
    private TokenData getToken() {
        var key = "Token:" + tokenId;
        var json = Redis.get(key);

        return Json.toBean(json, TokenData.class);
    }

    /**
     * 用户是否已在其他设备登录
     *
     * @return 是否已在其他设备登录
     */
    private boolean loginElsewhere() {
        Long appId = basis.getAppId();
        var type = Redis.get("App:" + appId, "SignInType");
        if (!Boolean.parseBoolean(type)) {
            return false;
        }

        var key = "UserToken:" + userId;
        var value = Redis.get(key, appId.toString());

        return !tokenId.equals(value);
    }

    /**
     * 用户是否被禁用
     *
     * @return 是否被禁用
     */
    private boolean invalid() {
        var key = "User:" + userId;
        var value = Redis.get(key, "invalid");
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
        var permits = basis.getPermitFuncs();
        return permits != null && !permits.isEmpty() && permits.stream().anyMatch(authCode::equalsIgnoreCase);
    }
}

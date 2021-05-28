package com.insight.gateway.common;

import com.insight.utils.Json;
import com.insight.utils.Redis;
import com.insight.utils.Util;
import com.insight.utils.common.ApplicationContextHolder;
import com.insight.utils.pojo.AccessToken;
import com.insight.utils.pojo.Reply;
import com.insight.utils.pojo.TokenInfo;
import com.insight.utils.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author 宣炳刚
 * @date 2019/05/20
 * @remark 用户身份验证类
 */
public class Verify {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Core core = ApplicationContextHolder.getContext().getBean(Core.class);
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
    private TokenInfo basis;

    /**
     * 缓存中的用户信息
     */
    private User user;

    /**
     * 令牌ID
     */
    private Long tokenId;

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
        AccessToken accessToken = Json.toAccessToken(token);
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
        Map<Object, Object> map = Redis.getEntity("User:" + userId);
        user = Json.clone(map, User.class);
        if (!basis.getAutoRefresh()) {
            return;
        }

        // 如果Token失效或过期时间大于一半,则不更新过期时间和失效时间.
        if (basis.isFailure() || !basis.isHalfLife()) {
            return;
        }

        int timeOut = TokenInfo.TIME_OUT;
        long life = basis.getLife();
        LocalDateTime now = LocalDateTime.now();
        basis.setExpiryTime(now.plusSeconds(timeOut + life));
        basis.setFailureTime(now.plusSeconds(timeOut + life * 12));

        long expire = timeOut + life * 12;
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
        if (isInvalid()) {
            return ReplyHelper.forbid(requestId);
        }

        if (isLoginElsewhere()) {
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

        if (basis.isFailure()) {
            return ReplyHelper.invalidToken(requestId);
        }

        // 无需鉴权,返回成功
        if (authCode == null || authCode.isEmpty()) {
            return ReplyHelper.success();
        }

        // 进行鉴权,返回鉴权结果
        if (isPermit(authCode)) {
            return ReplyHelper.success();
        }

        String account = user.getAccount();
        logger.warn("requestId: {}. 告警信息: {}", requestId, "用户『" + account + "』试图使用未授权的功能:" + authCode);

        return ReplyHelper.noAuth(requestId);
    }

    /**
     * 获取令牌中的用户ID
     *
     * @return 是否同一用户
     */
    public boolean userIsEquals(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 获取令牌持有人的应用ID
     *
     * @return 应用ID
     */
    public Long getAppId() {
        return basis.getAppId();
    }

    /**
     * 获取令牌持有人的租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() {
        return basis.getTenantId();
    }

    /**
     * 获取令牌持有人的用户ID
     *
     * @return 用户ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取令牌持有人的用户名
     *
     * @return 用户名
     */
    public String getUserName() {
        return user.getName();
    }

    /**
     * 根据令牌ID获取缓存中的Token
     *
     * @return TokenInfo(可能为null)
     */
    private TokenInfo getToken() {
        String key = "Token:" + tokenId;
        String json = Redis.get(key);

        return Json.toBean(json, TokenInfo.class);
    }

    /**
     * 用户是否已在其他设备登录
     *
     * @return 是否已在其他设备登录
     */
    private boolean isLoginElsewhere() {
        Long appId = basis.getAppId();
        String type = Redis.get("App:" + appId, "SignInType");
        if (!Boolean.parseBoolean(type)) {
            return false;
        }

        String key = "UserToken:" + userId;
        String value = Redis.get(key, appId.toString());

        return !tokenId.toString().equals(value);
    }

    /**
     * 用户是否被禁用
     *
     * @return 是否被禁用
     */
    private boolean isInvalid() {
        String key = "User:" + userId;
        String value = Redis.get(key, "invalid");
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
        if (basis.isPermitExpiry()) {
            basis.setPermitFuncs(core.getPermits(requestId, Json.toBase64(this)));
            basis.setPermitTime(LocalDateTime.now());
            Redis.set("Token:" + tokenId, basis.toString());
        }

        List<String> permits = basis.getPermitFuncs();
        return permits != null && !permits.isEmpty() && permits.stream().anyMatch(authCode::equalsIgnoreCase);
    }
}

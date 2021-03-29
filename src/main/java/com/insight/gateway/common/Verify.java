package com.insight.gateway.common;

import com.insight.utils.*;
import com.insight.utils.common.ApplicationContextHolder;
import com.insight.utils.pojo.AccessToken;
import com.insight.utils.pojo.Reply;
import com.insight.utils.pojo.TokenInfo;
import com.insight.utils.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author 宣炳刚
 * @date 2019/05/20
 * @remark 用户身份验证类
 */
public class Verify {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Core core = ApplicationContextHolder.getContext().getBean(Core.class);

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
    private String tokenId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 构造方法
     *
     * @param token       访问令牌
     * @param fingerprint 用户特征串
     */
    public Verify(String token, String fingerprint) {
        hash = Util.md5(token + fingerprint);
        AccessToken accessToken = Json.toAccessToken(token);
        if (accessToken == null) {
            logger.error("提取验证信息失败。Token is:" + token);

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

        // 如果Token失效,则不更新过期时间和失效时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(basis.getFailureTime())) {
            return;
        }

        // 判断Token剩余的过期时间.如果过期时间大于一半,则不更新过期时间和失效时间
        Duration duration = Duration.between(now, basis.getExpiryTime());
        long life = basis.getLife();
        if (duration.toMillis() > life / 2) {
            return;
        }

        int timeOut = TokenInfo.TIME_OUT;
        basis.setExpiryTime(now.plusSeconds(timeOut + (life / 1000)));
        basis.setFailureTime(now.plusSeconds(timeOut + (life / 1000)));

        long expire = (timeOut * 1000) + (life * 12);
        Redis.set("Token:" + tokenId, basis.toString(), expire, TimeUnit.MILLISECONDS);
    }

    /**
     * 验证Token合法性
     *
     * @param authCode 接口授权码
     * @return Reply Token验证结果
     */
    public Reply compare(String authCode) {
        if (basis == null) {
            return ReplyHelper.invalidToken();
        }

        // 验证用户
        if (isInvalid()) {
            return ReplyHelper.forbid();
        }

        if (isLoginElsewhere()) {
            return ReplyHelper.fail("您的账号已在其他设备登录");
        }

        // 验证令牌
        if (basis.getVerifySource()) {
            if (!basis.verifyTokenHash(hash)) {
                return ReplyHelper.invalidToken();
            }
        } else {
            if (!basis.verifySecretKey(secret)) {
                return ReplyHelper.invalidToken();
            }
        }

        if (basis.isExpiry()) {
            return ReplyHelper.expiredToken();
        }

        if (basis.isFailure()) {
            return ReplyHelper.invalidToken();
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
        logger.warn("用户『" + account + "』试图使用未授权的功能:" + authCode);

        return ReplyHelper.noAuth();
    }

    /**
     * 获取令牌中的用户ID
     *
     * @return 是否同一用户
     */
    public boolean userIsEquals(String userId) {
        return this.userId.equals(userId);
    }

    /**
     * 获取令牌持有人的应用ID
     *
     * @return 应用ID
     */
    public String getAppId() {
        return basis.getAppId();
    }

    /**
     * 获取令牌持有人的租户ID
     *
     * @return 租户ID
     */
    public String getTenantId() {
        return basis.getTenantId();
    }

    /**
     * 获取令牌持有人的用户ID
     *
     * @return 用户ID
     */
    public String getUserId() {
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
        String appId = basis.getAppId();
        String type = Redis.get("App:" + appId, "SignInType");
        if (!Boolean.parseBoolean(type)) {
            return false;
        }

        String key = "UserToken:" + userId;
        String value = Redis.get(key, appId);

        return !tokenId.equals(value);
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
            basis.setPermitFuncs(core.getPermits(Json.toBase64(this)));
            basis.setPermitTime(LocalDateTime.now());
            Redis.set("Token:" + tokenId, basis.toString());
        }

        List<String> permits = basis.getPermitFuncs();
        if (permits == null || permits.isEmpty()) {
            return false;
        }

        return permits.stream().anyMatch(authCode::equalsIgnoreCase);
    }
}

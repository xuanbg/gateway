# 基于 Spring Cloud Gateway 的网关使用说明

## 主要功能

通过对Http请求的拦截,根据接口配置数据实现对接口访问的限流和身份验证及鉴权功能.同时也在Info级别日志中输出请求参数/返回数据以及接口响应时间.
网关在转发请求前,将会添加以下请求头:

|请求头|说明|
|---|---|
|requestId|请求ID,用于调用链路跟踪|
|fingerprint|客户端指纹,用于鉴别来源|
|loginInfo|包含应用ID/租户ID/用户ID等用户关键信息|

>网关的部分功能依赖于其他项目的配合

### 限流

接口限流可同时实现两种模式:

1. 间隔限制模式.同一来源对接口的调用,必须大于设定的最小调用时间间隔.如调用间隔低于1秒,则重新进行计时作为惩罚.
2. 次数限制模式.同一来源在单位时间内,调用次数不得高于设定的最大调用次数.限流周期从第一次调用开始计时,计时结束后,从下一次调用开始重新计时.

如满足限流条件,则返回请求过于频繁的错误(490).如需实现接口限流,请在接口配置数据中配置以下参数:

|参数|是否必需|说明|
|---|---|---|
|isLimit|是|是否限流,如该参数配置成false,则下面3个参数都不会起作用|
|limitGap|否|最小调用时间间隔(秒)|
|limitCycle|否|限流周期(秒)|
|limitMax|否|最大调用次数/限流周期|
|message|否|触发限流时反馈的错误消息|
>如未配置任何限流参数,即使isLimit为true也不能实现限流

限流相关代码如下:

```java
if (config.getLimit()) {
    String key = method + path;
    String limitKey = Util.md5(fingerprint + key);
    if (isLimited(Util.md5(key), config.getLimitGap(), config.getLimitCycle(), config.getLimitMax(), config.getMessage())) {
        return initResponse(exchange);
    }
}
```

```java
/**
 * 是否被限流
 *
 * @param key   键值
 * @param gap   访问最小时间间隔(秒)
 * @param cycle 限流计时周期(秒)
 * @param max   限制次数/限流周期
 * @param msg   消息
 * @return 是否限制访问
 */
private boolean isLimited(String key, Integer gap, Integer cycle, Integer max,String msg) {
    return isLimited(key, gap) || isLimited(key, cycle, max, msg);
}
```

```java
/**
 * 是否被限流(访问间隔小于最小时间间隔)
 *
 * @param key 键值
 * @param gap 访问最小时间间隔
 * @return 是否限制访问
 */
private boolean isLimited(String key, Integer gap) {
    if (key == null || key.isEmpty() || gap == null || gap.equals(0)) {
        return false;
    }

    key = "Surplus:" + key;
    String val = Redis.get(key);
    if (val == null || val.isEmpty()) {
        Redis.set(key, DateHelper.getDateTime(), gap, TimeUnit.SECONDS);
        return false;
    }

    Date time = DateHelper.parseDateTime(val);
    long bypass = System.currentTimeMillis() - Objects.requireNonNull(time).getTime();

    // 调用时间间隔低于1秒时,重置调用时间为当前时间作为惩罚
    if (bypass < 1000) {
        Redis.set(key, DateHelper.getDateTime(), gap, TimeUnit.SECONDS);
    }

    reply = ReplyHelper.tooOften();
    return true;
}
```

```java
/**
 * 是否被限流(限流计时周期内超过最大访问次数)
 *
 * @param key   键值
 * @param cycle 限流计时周期(秒)
 * @param max   限制次数/限流周期
 * @param msg   消息
 * @return 是否限制访问
 */
private Boolean isLimited(String key, Integer cycle, Integer max, String msg) {
    if (key == null || key.isEmpty() || cycle == null || cycle.equals(0) || max == null || max.equals(0)) {
        return false;
    }

    // 如记录不存在,则记录访问次数为1
    key = "Limit:" + key;
    String val = Redis.get(key);
    if (val == null || val.isEmpty()) {
        Redis.set(key, "1", cycle, TimeUnit.SECONDS);

        return false;
    }

    // 读取访问次数,如次数超过限制,返回true,否则访问次数增加1次
    Integer count = Integer.valueOf(val);
    long expire = Redis.getExpire(key, TimeUnit.SECONDS);
    if (count > max) {
        reply = ReplyHelper.tooOften(msg);

        return true;
    }

    count++;
    Redis.set(key, count.toString(), expire, TimeUnit.SECONDS);

    return false;
}
```

### 身份验证及鉴权

只需在接口配置中设置isVerify参数为true,即可开启接口的身份验证功能.如设置了authCode参数,则在通过身份验证后再进行鉴权.鉴权的依据来自于对用户的授权数据(需要在资源中设置相应的授权码),授权数据会在用户获取Token时加载到Redis并与Token绑定.

相关代码如下:

```java
if (config.getVerify() && !verify(headers.getFirst("Authorization"), fingerprint config.getAuthCode())) {
    return initResponse(exchange);
}
```

```java
/**
 * 验证用户令牌并鉴权
 *
 * @param token       令牌
 * @param fingerprint 用户特征串
 * @param authCodes   接口授权码
 * @return 是否通过验证
 */
private boolean verify(String token, String fingerprint, String authCodes) {
    if (token == null || token.isEmpty()) {
        reply = ReplyHelper.invalidToken();

        return false;
    }

    Verify verify = new Verify(token, fingerprint);
    reply = verify.compare(authCodes);
    if (!reply.getSuccess()) {
        return false;
    }

    TokenInfo basis = verify.getBasis();
    loginInfo.setAppId(basis.getAppId());
    loginInfo.setTenantId(basis.getTenantId());
    loginInfo.setDeptId(basis.getDeptId());
    loginInfo.setUserId(verify.getUserId());
    loginInfo.setUserName(verify.getUserName());

    return true;
}
```

```java
public class Verify {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 令牌哈希值
     */
    private final String hash;

    /**
     * 缓存中的令牌信息
     */
    private TokenInfo basis;

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
        // 初始化参数
        hash = Util.md5(token + fingerprint);
        AccessToken accessToken = Json.toAccessToken(token);
        if (accessToken == null) {
            logger.error("提取验证信息失败。TokenManage is:" + token);

            return;
        }

        tokenId = accessToken.getId();
        userId = accessToken.getUserId();
        basis = getToken();
    }

    /**
     * 验证Token合法性
     *
     * @param authCodes 接口授权码
     * @return Reply Token验证结果
     */
    public Reply compare(String authCodes) {
        if (basis == null) {
            return ReplyHelper.invalidToken();
        }

        if (isInvalid()) {
            return ReplyHelper.fail("用户已被禁用");
        }

        // 验证令牌
        if (!basis.verifyToken(hash)) {
            return ReplyHelper.invalidToken();
        }

        if (basis.isExpiry(true)) {
            return ReplyHelper.expiredToken();
        }

        if (basis.isFailure()) {
            return ReplyHelper.invalidToken();
        }

        // 无需鉴权,返回成功
        if (authCodes == null || authCodes.isEmpty()) {
            return ReplyHelper.success();
        }

        // 进行鉴权,返回鉴权结果
        if (isPermit(authCodes)) {
            return ReplyHelper.success();
        }

        String account = getUser().getAccount();
        logger.warn("用户『" + account + "』试图使用未授权的功能:" + authCodes);

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
     * 获取缓存中的Token
     *
     * @return TokenInfo
     */
    public TokenInfo getBasis() {
        return basis;
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
        return getUser().getName();
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
     * 用户是否被禁用
     *
     * @return User(可能为null)
     */
    private boolean isInvalid() {
        String key = "User:" + userId;
        String value = Redis.get(key, "IsInvalid");
        if (value == null) {
            return true;
        }

        return Boolean.parseBoolean(value);
    }

    /**
     * 读取缓存中的用户数据
     *
     * @return 用户数据
     */
    private User getUser() {
        String key = "User:" + userId;
        String value = Redis.get(key, "User");

        return Json.toBean(value, User.class);
    }

    /**
     * 指定的功能是否授权给用户
     *
     * @param authCodes 接口授权码
     * @return 功能是否授权给用户
     */
    private Boolean isPermit(String authCodes) {
        List<String> functions = basis.getPermitFuncs();
        String[] codes = authCodes.split(",");
        for (String code : codes) {
            if (functions.stream().anyMatch(i -> i.equalsIgnoreCase(code))) {
                return true;
            }
        }

        return false;
    }
}
```

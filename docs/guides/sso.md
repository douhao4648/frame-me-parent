# SSO 单点登录接入指南

> 本文档面向需要接入 SSO 的应用（集群内自家应用、集群外三方应用），说明接入流程、token 验证、踢人机制。
>
> **架构模式：RP session**。SSO 只管发身份凭证（sa-token 不透明 token），下游应用拿 token 调 `/userinfo` 取用户信息后建自己的 sa-token session，后续鉴权用下游自己的 sa-token，不依赖 SSO 运行时。

## 架构概览

SSO 服务（`frame-me-sso-service`）是独立 Spring Boot 认证服务，提供：

- **应用注册表**：登记内部/外部应用，分配 `appId`（外部额外分配 `appSecret`）
- **授权码流程**：`/api/auth/authorize` → 登录（`POST /base/auth/login`）→ 回调带 code → `/api/auth/token` 换 sa-token 不透明 token
- **/userinfo 端点**：`GET /api/users/info`，下游凭 token 调用，SSO 用 sa-token 原生验 token（`SsoStpUtil.stpLogic.getLoginIdByToken`），返回用户信息
- **踢人（档3事件）**：SSO 踢人 `SsoStpUtil.stpLogic.logout(userId)` 即时清 SSO 侧 sa-token 会话 + 发 `UserLogoutEvent` 跨进程广播，下游订阅清自己 session

### 账号体系：独立 `sso` loginType

SSO 服务整体使用 sa-token 多账号体系中的独立 `sso` 体系（`SsoStpUtil.stpLogic`，即 `new StpLogic("sso")`），与其他服务默认的 `login` 体系（`StpUtil`）共存：

- 配置：`me.auth.sa-token.logic-type: sso`（starter 全部认证动作改走该体系）；业务侧经 `SsoStpUtil.stpLogic` 调用，`@SaCheck*` 注解加 `type = SsoStpUtil.TYPE`
- Redis key：`satoken:sso:token|session:*`（key 规则 `{tokenName}:{loginType}:*`），与下游的 `satoken:login:*` 在同一 Redis 中命名空间隔离、互不串号
- 请求头/Cookie 名仍为全局 `sa-token.token-name`（`satoken`），两体系共用通道名但 key 空间不同

### token 体系

SSO 颁发的是 **sa-token 不透明 token**（非 JWT），带 **app 维度 + 独立时效**：

- 签发：`SsoStpUtil.stpLogic.createLoginSession(userId, SaLoginParameter().setDeviceType(appId).setTimeout(7d))`，不依赖请求上下文（下游服务端 POST 调用无浏览器上下文）；签发后调用 `SaTokenAuthService.markLoginTime(SsoStpUtil.stpLogic, loginId)` 补记登录时间戳——该路径绕过了 starter 的 `login/loginByUser`，不补记则 refresh 的绝对寿命闸门（`me.auth.sa-token.max-lifetime`）会从第一次续期起算（宽限路径）
- app 维度：`deviceType=appId`，会话绑定具体应用，可按 app 单独踢人
- 独立时效：`me.sso.token.app-timeout`（默认 1d），与浏览器登录会话（`sa-token.timeout` 7d + `active-timeout` 8h 闲置冻结）分开，互不影响
- 验证：`SsoStpUtil.stpLogic.getLoginIdByToken(token)`，查 sa-token Redis（sso 体系），原生能力，零自写验签代码
- 下游不自己验 token，调 `/userinfo` 由 SSO 代劳验证 + 返回用户信息
- 无 JWT、无公钥私钥、无 jjwt 依赖

> **安全基线**：`authorize` 强制校验 redirectUri 白名单与 scope 白名单（请求 scope 须 ⊆ 应用注册 scopes）；支持 `state` 参数原样回显（防登录 CSRF，下游生成并比对）；授权码一次性（Redis GETDEL 原子消费，60s 过期）；登录页 `/sso-login.html` 在 `me.auth.whitelist` 中匿名放行，且登录成功后的回跳地址仅允许站内相对路径（防 open redirect）。**管理端点（`/api/apps/**`、踢人、用户 CRUD）加设备闸**（`DefaultDeviceInterceptor`）：仅接受默认设备会话（deviceType=DEF，即 SSO 登录会话），SSO 下发的应用 token（deviceType=appId）即使放入 satoken 头也 403，堵住"窄钥匙开管理门"；匿名请求直接放行（`@Anonymous` 端点自验 token，受保护端点由 `@SaCheckRole` 拦 401）。开关与拦截路径可配：`me.sso.device-gate.enabled`（默认开）、`me.sso.device-gate.path-patterns`（默认 `/api/apps/**`、`/api/auth/*/logout`、`/api/users/**`）。

> **边界**：/userinfo 不校验 token 的 app 受众（不透明 token 模式下 deviceType 校验链路重，且 /userinfo 只返基础信息风险可控）。演进 OIDC 时 JWT 的 `aud` claim 天然解决受众校验。

## 应用类型

| 类型 | `access_type` | 凭证 | 换 token 验密钥 |
|---|---|---|---|
| 集群内应用 | `INTERNAL` | `appId` + `appSecret` | 强制 |
| 集群外三方 | `EXTERNAL` | `appId` + `appSecret` | 强制 |

"内部/外部"是 SSO 登记关系维护（标识归属与审计），两种类型注册时均分配 `appSecret`，换 token 一律强制验密钥。

## 内部应用接入

1. 管理员在 SSO 管理端注册应用：
   ```bash
   curl -X POST http://sso:10010/api/apps/ \
     -H 'satoken: <admin-token>' -H 'Content-Type: application/json' \
     -d '{"appName":"订单服务","accessType":"INTERNAL","redirectUris":["http://order.svc/cb"],"scopes":"openid"}'
   ```
   返回 `appId`（如 `fm-internal-xxxx`）+ `appSecret`（**明文仅此一次返回**，妥善保管）。

2. 用户访问应用 → 应用重定向到 SSO 授权（`state` 下游生成，SSO 原样回显）：
   ```
   GET http://sso:10010/api/auth/authorize?appId=fm-internal-xxxx&redirectUri=http://order.svc/cb&scope=openid&state=<random>
   ```
   - 未登录 → 重定向登录页 → 用户登录 → 回调 `http://order.svc/cb?code=xxx&state=<random>`
   - 已登录 → 直接回调带 code

3. 应用用 code 换 token（**必须带 appSecret**）：
   ```bash
   curl -X POST http://sso:10010/api/auth/token \
     -H 'Content-Type: application/json' \
     -d '{"code":"xxx","appId":"fm-internal-xxxx","appSecret":"<secret>","redirectUri":"http://order.svc/cb"}'
   ```
   返回 `{ "data": { "accessToken": "<sa-token>" } }`。

## 外部三方应用接入

流程与内部应用完全一致（注册 `accessType=EXTERNAL`，授权码 + secret 换 token），
区别仅是登记关系标识，用于归属审计。

## 机器对机器调用（client_credentials）

定时任务/服务间调用没有用户参与，走不了授权码流程，用 client_credentials 直换**应用 token**：

```bash
curl -X POST http://sso:10010/api/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"grantType":"client_credentials","appId":"fm-external-xxxx","appSecret":"<secret>"}'
```

- 无需 code / redirectUri；INTERNAL / EXTERNAL 均可用（两类型注册时都发 `appSecret`，强制验密钥）
- **按 appId 限流**：复用 `LoginRateLimiter`（`me.auth.login-rate-limit.*`，默认 5 次/60s），防 appSecret 爆破；不用 IP 维度（M2M 调用方可能合法突发，爆破必然聚焦单个 appId）。authorization_code 模式先过一次性 code 消耗、不可用于爆破密钥，故不限流
- token 的 loginId 是 `"app:"+appId`（**主体是应用自身，无用户维度**），deviceType=appId，时效同 `app-timeout`
- 该 token 调 `/userinfo` 返 401（无对应用户），只用于机器接口调用
- 续期同用户 token：`POST /base/auth/refresh` 带当前 token 即可

> **下游开箱方法**：`frame-me-sso-starter` 的 `SsoAuthService.getAppToken()` 已封装上述
> 换取 + 缓存逻辑——token 缓存在实例内存（单条目：一个服务只持有自己 appId 的 token；
> `me.sso.client.app-token-cache-ttl`，默认 1h，须小于 SSO 侧 `app-timeout`），
> 过期后下次调用自动重取，并避免高频重取触发 SSO 的 appId 限流；持缓存 token 调 SSO
> 遇 401 时调 `invalidateAppToken()` 后重试。
> 缓存为实例级（不跨实例共享），多实例各自换 token 在限流额度内无害。

## 下游建 session（RP session 模式核心）

下游拿到 SSO 的 sa-token 后，**不直接用它做日常鉴权**，而是：

1. **调 /userinfo 取用户信息**（SSO 代劳验 token）：
   ```bash
   curl http://sso:10010/api/users/info \
     -H 'Authorization: Bearer <sa-token>'
   ```
   返回 `{ "data": { "sub":"1001", "account":"alice", "name":"Alice", "roles":"admin" } }`。
   - SSO 用 `SsoStpUtil.stpLogic.getLoginIdByToken` 验 token（查 sa-token Redis 的 sso 体系），无效返 401
   - 用户信息走 L1/L2 缓存（60s，写路径同步失效），改名不需重签 token
   - 下游不自己验 token，零验签代码、零密钥管理

2. **建下游自己的 sa-token session**：
   ```java
   // 下游应用用现有 frame-me-starter-auth-sa-token
   StpUtil.login(userId);
   // 之后请求用下游自己的 sa-token，不再碰 SSO
   ```

SSO 颁发的 token 在下游只用于"调 /userinfo 取用户信息建 session"，不做日常鉴权凭证。

> **上游 token 留存**：`SsoAuthService.ssoLogin` 建本地会话后会调
> `IAuthService.storeUpstreamToken(userId, appId, ssoToken)` 按应用隔离地留存 SSO token，
> 供下游后续回源调 SSO 接口（如本地会话 miss 时重拉 /userinfo 重建）。
> 存储随本地会话同生共死：sa-token 实现写 Account-Session（key `upstreamToken:{appId}`，
> 注销会话即自动清除）；JWT 实现经 `IRefreshTokenStore` 落 Redis hash（key =
> `me.auth.jwt.upstream-token-prefix` + userId（默认 `auth:upstream:{userId}`），
> field = appId，TTL 对齐 Refresh Token 时效并随 `refresh` 续期，
> `logout`/`logoutByUserId` 同步清除）。回源取数统一走
> `SsoAuthService.loadUserByUpstreamToken(userId)`（取留存 token → 重拉 /userinfo →
> 重建 User，含 sub 一致性校验防串号，取不到/失败返回 null 即 fail-closed）——RP 下游
> （无本地用户表）无需自己实现 `IAuthUserDetailsService`：starter 在未自定义时兜底装配
> `SsoAuthUserDetailsService`（`loadUserById` 即委托该方法），自定义则自动退让。
> **appId 隔离的意义**：不同应用的 token 互不覆盖，共用存储后端的多个下游结构性免疫互撞。
> **安全约定**：SSO token 是 bearer 凭证，只存服务端，不得写入下发客户端的凭证（如 JWT claims）。
> 注意留存的 token 是 app 维度凭证（`deviceType=appId`），受 SSO 设备闸限制，
> 只能调 `/userinfo`/`/base/auth/refresh`，调管理端点（`/api/apps/**` 等）会 403；
> 机器级调用请走 client_credentials 应用 token。
>
> **多服务共用 Redis 的命名空间前提**（sa-token 下游）：Account-Session key 为
> `{tokenName}:{loginType}:session:{userId}`——若多个 sa-token 下游共用 Redis 且全用默认
> `satoken`/`login`，它们共享整个 Account-Session（用户快照、`loginTime` 与全部
> `upstreamToken:*` 都互写，audit 可能取出 order 的 token，/userinfo 不校验
> app 受众"碰巧能用"，但按应用踢人会误伤）。正确做法是各服务配独立
> `me.auth.sa-token.logic-type`（或 `sa-token.token-name`，或独立 Redis db）。
> JWT 下游无此问题：appId 在 hash field 上天然隔离。

> **开箱端点**：上述 RP 登录编排（code 换 token → /userinfo → 建本地会话）已封装进
> `frame-me-sso-starter` 的 `SsoAuthController` + `SsoAuthService`，提供
> `POST /api/auth/sso-login` 端点。下游引 `frame-me-sso-starter` + `frame-me-starter-auth-sa-token`
> 或 `frame-me-starter-auth-jwt` 即开箱获得该端点，无需自写 controller。
>
> **开箱回调落地页**：starter 同时提供 `GET /index`（`SsoIndexController` + 内置
> `sso/index.html`，匿名）。把 `me.sso.client.redirect-uri` 配为该端点
> （如 `http://your-app/index`），authorize 回调的 code/state 即落在该页：
> 页面 JS 校验 state（仅站内相对路径，缺失/为空/不合法回落 `/`，防 open redirect），
> 然后经 URL hash 携带 code 重定向到 state 标记的站内地址（hash 不进服务端日志/Referer），
> 目标页（SPA 路由）从 `location.hash` 取 code 调 `/sso-login` 完成登录；
> 无 code 的直达访问不跳转（防自转循环）。
>
> **认证实现通用**：端点依赖 `IAuthService.loginByUser`，sa-token/JWT 两套实现均覆盖
> （`SaTokenAuthService` 建会话 + Account-Session 快照缓存；`JwtTokenService` 建 token 对并写入
> `rp`/`nickname` 快照 claims，`getUser`/`refresh` 在 `loadUserById` miss 时从 claims 重建 User），下游任选其一。

## 踢人机制

下游有自己的 sa-token session，**SSO 踢不到下游 session**。踢人靠两层兜底：

| 层 | 机制 | 即时性 |
|---|---|---|
| SSO 侧（默认） | `SsoStpUtil.stpLogic.logout(userId)` 清 SSO 的 sa-token 会话（含给下游的 token） | 即时，下游持有的 SSO token 立即失效 |
| 下游侧（可选） | 档3事件：SSO 发 `UserLogoutEvent`，下游订阅清自己 sa-token session | 事件到达即清 |

### SSO 侧踢人

`POST /api/auth/{userId}/logout?appId=xxx&reason=xxx`：
- 传 `appId` → `SsoStpUtil.stpLogic.logout(userId, appId)` 只清该用户在该 app 的会话（deviceType 匹配），其他 app 会话和浏览器登录态不受影响
- 不传 `appId` → `SsoStpUtil.stpLogic.logout(userId)` 清该用户所有 sa-token 会话（含浏览器登录态 + 各 app 的下游 token）
- 给下游的 token 立即失效，下游调 /userinfo 会 401
- 但下游**已建的 sa-token session** SSO 清不到，靠下游 session 短时效兜底（建议配 30min~2h）或档3事件即时清

`POST /api/apps/{appId}/logout?reason=xxx`（按应用踢，admin）：
- 注销该 appId 下**全部**会话：client_credentials 应用 token（loginId="app:"+appId）+ 所有用户 token（deviceType=appId 的 terminal，遍历会话精确匹配）
- 返回踢掉的会话数；档3事件 payload 的 userId 为 null，下游应以 appId 清该应用全部本地 session

`POST /api/auth/logout`（全局登出，用户触发，OIDC single logout 对应物）：
- 当前登录用户一键全退：清 SSO 浏览器会话 + 全部应用 token，广播档3事件（userId 有值、appId=null）通知所有下游清本地 session
- `@SaCheckLogin` 保护；应用 token（client_credentials，无用户维度）调用在 AuthFilter 层即 401
- 与 `/base/auth/logout` 的区别：后者只注销当前单条 token 会话、**无事件通知**，适合应用级局部登出

### 档3事件（即时清下游 session）

SSO 踢人时通过事件桥接发布 `UserLogoutEvent`（type=`sso:user-logout`），经 Redis pub/sub 跨进程广播。事件契约在 `frame-me-sso-api`（`com.frame.me.sso.event`），下游订阅：

- 引 `frame-me-sso-starter`（含事件契约 + `RedisEventTransport` + `SsoLogoutEventListener`，下游引 starter 即开箱获得踢人监听，无需自写 `@EventListener`）
- `@Import(UserLogoutEventConfiguration.class)` 注册事件类型（已由 `SsoClientAutoConfiguration` 自动 `@Import`；SSO 服务自身无需显式引入：该配置类包 `com.frame.me.sso.event` 在启动类扫描根包 `com.frame.me.sso` 之下，组件扫描自动注册，保证多实例广播互通）
- `SsoLogoutEventListener` 收到 `UserLogoutEvent` 后调 `IAuthService.logoutByUserId(userId)` 清本地会话——走认证 SPI，sa-token 清 sa-token 会话，JWT 删 Refresh Token，两套认证实现通用；`userId=null` 时（按应用踢）暂不处理

事件 payload：`{ userId, appId, logoutTime, reason }`。消费方需幂等（跨服务事件"至少一次"语义）。

## 管理端点（需 admin 角色）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/apps/` | 注册应用（`@Valid`：appName 非空、accessType 仅 INTERNAL/EXTERNAL、redirectUris 非空） |
| POST | `/api/apps/{appId}` | 更新应用（`@Valid`：status 仅 ACTIVE/DISABLED，null 表示不更新） |
| POST | `/api/apps/{appId}/reset-secret` | 重置 EXTERNAL 密钥 |
| POST | `/api/apps/{appId}/disable` | 禁用应用（**禁用即生效**：联动踢出该应用全部存量会话） |
| POST | `/api/apps/{appId}/logout` | 按应用踢人（注销该 appId 全部会话：用户 token + 应用 token，发档3事件 userId=null） |
| GET | `/api/apps/` | 应用列表 |
| POST | `/api/auth/{userId}/logout` | 强制登出（踢人，清 SSO 会话 + 发档3事件） |
| POST | `/api/users/` | 创建用户（`@Valid`：account 格式、password 6-64、name 非空；账号唯一，BCrypt 入库） |
| GET | `/api/users/` | 用户列表（VO 永不含密码字段） |
| GET | `/api/users/{id}` | 用户详情 |
| POST | `/api/users/{id}` | 更新用户（name/password/roles/status，null 不更新；改密码或置 DISABLED 联动踢出全部会话） |
| DELETE | `/api/users/{id}` | 删除用户（逻辑删除，联动踢出全部会话，不可再登录） |

用户管理端点防自锁：不能禁用/删除当前登录账号。

## 演进路径（标准 OIDC）

现期是自研轻量 SSO（RP session 模式 + sa-token 不透明 token），预留 OIDC 扩展点，演进是**叠加不推翻**：

| 现期 | 演进 OIDC | 动作 |
|---|---|---|
| `/authorize` + `/token` + `/login` 骨架 | 复用 | 补 PKCE |
| sa-token 不透明 token | 换 RS256 JWT（id_token + access_token） | 加 `IJwtSigner` + jjwt，签发侧换算法 |
| `/userinfo`（已有） | 标配 | 复用，验签从 sa-token 换 JWT 公钥验 |
| 无 refresh | 标准 refresh_token | 拆 token 类型后自然落 |
| 无 JWKS | `/.well-known/jwks.json` | 加公钥分发端点 |
| 无 discovery | `/.well-known/openid-configuration` | 新增 |
| 档3事件 | 标准 backchannel logout | 升级事件协议 |

> 现期选 sa-token 不透明 token 而非 JWT，是因为路2 RP session 下下游不自己验签（/userinfo 代劳），非对称加密的跨信任域验签价值不成立，整套密钥管理是冗余。演进 OIDC 时下游要本地验 id_token，那时才需要非对称密钥。

下游侧演进：建 session 时改验 id_token（本地行为，各下游自己改），access_token 用来调 RS API。

## 下游接入（frame-me-sso-starter）

`frame-me-sso-starter` 是下游应用一键接入 SSO 的公共 starter，与认证底座（sa-token / JWT）解耦。引它即获得：SSO HTTP Interface 客户端代理 + 踢人事件订阅。

### 引入与配置

```xml
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-sso-starter</artifactId>
</dependency>
```

```yaml
me:
  sso:
    client:
      enabled: true
      app-id: fm-internal-your-app      # 本应用在 SSO 注册的应用 ID
      app-secret: ME(密文)              # 应用密钥，支持 sensi-encrypt 加密
      redirect-uri: http://your-app/cb  # 授权码回调地址
spring:
  http:
    serviceclient:
      sso:
        base-url: http://frame-me-sso:10010   # SSO 服务地址（group=sso 的 baseUrl）
        read-timeout: 10s
```

### 注入 SSO 客户端

starter 通过 `@ImportHttpServices(group="sso")` 注册了 `IAuthApi` / `IUserApi` / `IAppApi` 的 HTTP Interface 代理 Bean，下游直接注入即可调用 SSO：

```java
@Service
@RequiredArgsConstructor
public class YourAuthService {
    private final IAuthApi authApi;    // 换 token（授权码 / client_credentials）
    private final IUserApi userApi;   // /userinfo 取用户信息

    public UserInfoVO loginByCode(String code) {
        // 1. code 换 SSO sa-token
        TokenRequestDTO req = new TokenRequestDTO();
        req.setGrantType("authorization_code");
        req.setCode(code);
        req.setAppId(appId);
        req.setAppSecret(appSecret);
        req.setRedirectUri(redirectUri);
        String ssoToken = authApi.token(req).getData().getAccessToken();

        // 2. SSO token 换用户信息（SSO 代劳验 token）
        return userApi.userinfo("Bearer " + ssoToken).getData();
    }
}
```

### 订阅踢人事件

starter 已 `@Import(UserLogoutEventConfiguration.class)` 注册 `UserLogoutEventType`，`EventBridgeListener` 自动订阅 `sso:user-logout` 通道。下游写 `@EventListener` 清自己的本地 session：

```java
@Component
public class YourLogoutListener {
    @EventListener
    public void onUserLogout(UserLogoutEvent event) {
        Long userId = event.getUserId();
        if (userId != null) {
            // sa-token 下游：清本地 sa-token 会话
            StpUtil.logout(userId);
            // JWT 下游：清 Redis 用户快照 + refresh token store
        }
    }
}
```

> 需引 `frame-me-starter-multi-redis`（提供 `RedisEventTransport`），否则跨服务踢人事件不可达。消费方需幂等（跨服务事件"至少一次"语义）。

### 建本地 session（认证底座自选）

starter 不绑认证底座，下游按需选择：

- **sa-token**：引 `frame-me-starter-auth-sa-token`，调 `IAuthService.loginByUser(user)` 建 sa-token 会话（`SaTokenAuthService` 覆盖了 default 方法，内部 `StpLogic.login` + 用户快照写 Account-Session 缓存）。
- **JWT**：引 `frame-me-starter-auth-jwt`，`JwtTokenService.loginByUser` 签发 token 对并写入 `rp`/`nickname` 快照 claims——下游无本地用户表（`loadUserById` 恒 null）时，`getUser`/`refresh` 从 claims 快照重建 User。

`IAuthService.loginByUser(User)` 是 RP 场景的统一入口（接口 default 抛异常，sa-token/JWT 两套实现均覆盖）。

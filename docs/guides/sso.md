# SSO 单点登录接入指南

> 本文档面向需要接入 SSO 的应用（集群内自家应用、集群外三方应用），说明接入流程、token 验证、踢人机制。
>
> **架构模式：RP session**。SSO 只管发身份凭证（sa-token 不透明 token），下游应用拿 token 调 `/userinfo` 取用户信息后建自己的 sa-token session，后续鉴权用下游自己的 sa-token，不依赖 SSO 运行时。

## 架构概览

SSO 服务（`frame-me-sso-service`）是独立 Spring Boot 认证服务，提供：

- **应用注册表**：登记内部/外部应用，分配 `appId`（外部额外分配 `appSecret`）
- **授权码流程**：`/api/auth/authorize` → 登录（`POST /base/auth/login`）→ 回调带 code → `/api/auth/token` 换 sa-token 不透明 token
- **/userinfo 端点**：`GET /api/users/info`，下游凭 token 调用，SSO 用 sa-token 原生验 token（`StpUtil.getLoginIdByToken`），返回用户信息
- **踢人（档3事件）**：SSO 踢人 `StpUtil.logout(userId)` 即时清 SSO 侧 sa-token 会话 + 发 `UserLogoutEvent` 跨进程广播，下游订阅清自己 session

### token 体系

SSO 颁发的是 **sa-token 不透明 token**（非 JWT），带 **app 维度 + 独立时效**：

- 签发：`StpUtil.createLoginSession(userId, SaLoginParameter().setDeviceType(appId).setTimeout(7d))`，不依赖请求上下文（下游服务端 POST 调用无浏览器上下文）
- app 维度：`deviceType=appId`，会话绑定具体应用，可按 app 单独踢人
- 独立时效：`me.sso.token.app-timeout`（默认 1d），与浏览器登录会话（`sa-token.timeout` 7d + `active-timeout` 2h 闲置冻结）分开，互不影响
- 验证：`StpUtil.getLoginIdByToken(token)`，查 sa-token Redis，原生能力，零自写验签代码
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
- token 的 loginId 是 `"app:"+appId`（**主体是应用自身，无用户维度**），deviceType=appId，时效同 `app-timeout`
- 该 token 调 `/userinfo` 返 401（无对应用户），只用于机器接口调用
- 续期同用户 token：`POST /base/auth/refresh` 带当前 token 即可

## 下游建 session（RP session 模式核心）

下游拿到 SSO 的 sa-token 后，**不直接用它做日常鉴权**，而是：

1. **调 /userinfo 取用户信息**（SSO 代劳验 token）：
   ```bash
   curl http://sso:10010/api/users/info \
     -H 'Authorization: Bearer <sa-token>'
   ```
   返回 `{ "data": { "sub":"1001", "account":"alice", "name":"Alice", "roles":"admin" } }`。
   - SSO 用 `StpUtil.getLoginIdByToken` 验 token（查 sa-token Redis），无效返 401
   - 用户信息现查库，改名不需重签 token
   - 下游不自己验 token，零验签代码、零密钥管理

2. **建下游自己的 sa-token session**：
   ```java
   // 下游应用用现有 frame-me-starter-auth-sa-token
   StpUtil.login(userId);
   // 之后请求用下游自己的 sa-token，不再碰 SSO
   ```

SSO 颁发的 token 在下游只用于"调 /userinfo 取用户信息建 session"，不做日常鉴权凭证。

## 踢人机制

下游有自己的 sa-token session，**SSO 踢不到下游 session**。踢人靠两层兜底：

| 层 | 机制 | 即时性 |
|---|---|---|
| SSO 侧（默认） | `StpUtil.logout(userId)` 清 SSO 的 sa-token 会话（含给下游的 token） | 即时，下游持有的 SSO token 立即失效 |
| 下游侧（可选） | 档3事件：SSO 发 `UserLogoutEvent`，下游订阅清自己 sa-token session | 事件到达即清 |

### SSO 侧踢人

`POST /api/auth/{userId}/logout?appId=xxx&reason=xxx`：
- 传 `appId` → `StpUtil.logout(userId, appId)` 只清该用户在该 app 的会话（deviceType 匹配），其他 app 会话和浏览器登录态不受影响
- 不传 `appId` → `StpUtil.logout(userId)` 清该用户所有 sa-token 会话（含浏览器登录态 + 各 app 的下游 token）
- 给下游的 token 立即失效，下游调 /userinfo 会 401
- 但下游**已建的 sa-token session** SSO 清不到，靠下游 session 短时效兜底（建议配 30min~2h）或档3事件即时清

`POST /api/apps/{appId}/logout?reason=xxx`（按应用踢，admin）：
- 注销该 appId 下**全部**会话：client_credentials 应用 token（loginId="app:"+appId）+ 所有用户 token（deviceType=appId 的 terminal，遍历会话精确匹配）
- 返回踢掉的会话数；档3事件 payload 的 userId 为 null，下游应以 appId 清该应用全部本地 session

### 档3事件（即时清下游 session）

SSO 踢人时通过事件桥接发布 `UserLogoutEvent`（type=`sso:user-logout`），经 Redis pub/sub 跨进程广播。下游订阅：

- 引 `frame-me-starter-multi-redis`（提供 `RedisEventTransport`）+ `frame-me-starter-base`（事件桥接核心）
- 订阅 type=`sso:user-logout`，收到后从 payload 取 `userId`，清本地 sa-token session（`StpUtil.logout(userId)`）

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

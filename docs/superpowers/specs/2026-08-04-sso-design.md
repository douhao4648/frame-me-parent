# frame-me-sso 单点登录服务设计

> 日期：2026-08-04
> 状态：设计完成，待用户复核后转入实现计划

## 背景与目标

`frame-me-launcher/frame-me-sso` 当前是纯占位空壳（仅有空 `SsoConstant` 类，pom 仅依赖 lombok）。本设计将其填充为一个独立的 **Spring Boot 认证服务**，提供：

- 集群内自家应用的统一登录（免注册分配 aksk，登记即接入）
- 集群外三方应用的统一登录（强制注册，分配 appId/appSecret 凭证）
- 用户一次登录，跨应用共享会话（SSO）

### 技术选型决策（已与用户确认）

| 决策点 | 选定方案 | 理由 |
|---|---|---|
| SSO 协议方向 | 自研轻量 SSO（授权码流程），预留 OIDC 扩展点 | 底座可复用，演进 OIDC 不推翻；sa-token-sso 私有协议会成演进包袱 |
| 持久层 | MyBatis-Flex（与 tester 一致） | 团队熟悉，与 tester-service 演示配置对齐 |
| token 校验 | JWT 自验签（RS256）+ SSO 侧 Redis 会话元数据 | 下游零 Redis 依赖；三方持公钥无法伪造；演进 OIDC 零换算法 |
| 签名算法 | RS256 非对称 | 三方只持公钥验签，杜绝伪造；OIDC 标准 |
| 用户账号 | SSO 自带用户库（`fm_sso_user`） | SSO 是唯一用户中心 |
| 登录页 | 默认内置简单 HTML 页，可配置切 JSON 接口 | 三方重定向过来有页面可看 |
| 集群内外判定 | SSO 登记关系维护（`access_type` 字段），非网络/cloud 判定 | 信任关系由 SSO 主动管理，与是否引 cloud 解耦 |

### 兼容性（已核实）

| 依赖 | 版本 | 核实结果 |
|---|---|---|
| Spring Boot | 4.0.7 | 已核实，根 pom BOM 管控 |
| Java | 25 | 已核实 |
| sa-token | 1.45.0（sa-token-spring-boot4-starter + sa-token-jwt） | 已核实，`frame-me-starter-auth-sa-token` 引入 |
| MyBatis-Flex | 与 tester 一致 | 已核实 |
| multi-redis | `frame-me-starter-multi-redis` | 已核实，sa-token Redis 会话 + 授权码存储 |

## 总体架构

### 模块定位

`frame-me-sso` 挂在 `frame-me-launcher` 下，是**独立可运行 Spring Boot 认证服务**（非 starter），与 `frame-me-gateway`/`frame-me-audit` 同级。本身是一个 Boot 应用（主启动类 + `application.yml` + management 端口分离），不是被别的服务引入的依赖。

```
frame-me-launcher/
  ├─ frame-me-sso      ← 本设计：独立 SSO 认证服务（Boot 应用）
  ├─ frame-me-gateway  （占位，本设计不涉及）
  └─ frame-me-audit    （占位，本设计不涉及）
```

### 依赖关系

```
frame-me-sso (Boot 应用)
  ├─ frame-me-starter-auth-sa-token   [会话治理 + JWT，传递引入 auth SPI + base(web/validation/actuator/restclient)]
  ├─ frame-me-starter-mybatis-flex     [应用注册表/授权码/用户库持久化]
  ├─ frame-me-starter-multi-redis     [SSO 自用：授权码 + 会话元数据 + 可选黑名单]
  ├─ frame-me-starter-sensi-encrypt   [appSecret / jwt 私钥 等敏感配置加密]
  ├─ frame-me-starter-op-audit        [应用注册/凭证重置/强制登出 审计]
  ├─ frame-me-starter-msg-notify      [凭证分配/重置 通知]
  └─ frame-me-starter-doc-openapi     [SpringDoc 文档]

不引：boot（避免多余 mvc 模块）、cloud/cloud-nacos（首期不接注册发现）、
      auth-rbac（用 sa-token 原生鉴权）、dynamic-ds、l1l2-cache、sse-mvc/ws-mvc
```

`frame-me-starter-auth-sa-token` 传递引入 `frame-me-starter-auth` → `frame-me-starter-base`，base 聚合 `spring-boot-starter-web`/`validation`/`actuator`/`restclient`/`hutool`/`fastjson2`/`frame-me-api`，Web MVC 底座不缺。

### 核心角色

| 角色 | 说明 |
|---|---|
| **SSO 服务（本模块）** | 用户登录、应用注册表管理、授权码签发、token 颁发、会话治理、踢人事件发送 |
| **内部应用**（`access_type=INTERNAL`） | 登记 in-app，只给 `appId` 不给 `appSecret`，code 换 token 免验密钥 |
| **外部三方应用**（`access_type=EXTERNAL`） | 登记 in-app，给 `appId`+`appSecret`，code 换 token 强制验密钥 |
| **下游应用**（内部/外部） | 持 SSO 公钥本地自验签 JWT，零 Redis 依赖；可选引 multi-redis 查黑名单 |
| **SSO 自身 Redis** | 仅 SSO 用：授权码、会话元数据、可选 token 黑名单。与下游 Redis 解耦 |

### 与现有 sa-token 的集成策略（策略 A）

SSO 实现**自己的 `IAuthService`**（`SsoAuthService`），覆盖 `SaTokenAuthService`（后者 `@ConditionalOnMissingBean` 退避）：

- `SsoAuthService.login()` 逻辑：校验授权码 → `StpUtil.login(userId)` → 缓存用户快照到 Account-Session → 返回 sa-token JWT token。
- sa-token 只提供会话治理底座（`StpUtil` + JWT Simple 模式 + Redis 会话），登录流程由 SSO 掌管。
- 下游应用引 `frame-me-starter-auth-sa-token`，用现有 `SaTokenAuthUserResolver` 解析 token，**零改动**。

## 模块详细设计

### 应用注册表与集群内外信任模型

#### 信任关系本质

"内部/外部"是 **SSO 主动维护的登记关系**，不是靠对方是否引 cloud / 是否同 K8s 网络判定。一个应用部署在同 K8s 走 svc 访问、但没引 cloud starter，只要 SSO 把它登记成 `INTERNAL`，它就是内部。

#### 数据库表设计

表前缀可配置（`me.sso.table-prefix`，默认 `fm_`），实体名剥离前缀。

**`fm_sso_app`（应用注册表）** → 实体 `SsoApp`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键（雪花 ID） |
| `app_id` | varchar(64) | 应用标识，唯一，SSO 分配（如 `fm-internal-xxx`） |
| `app_name` | varchar(128) | 应用名称 |
| `access_type` | varchar(16) | `INTERNAL` / `EXTERNAL` |
| `app_secret` | varchar(256) | 密钥；**INTERNAL 为 null**（免 aksk），EXTERNAL 存加密后的密钥 |
| `redirect_uris` | text | 回调白名单（JSON 数组），授权码发放时校验 |
| `scopes` | varchar(256) | 授权范围（如 `openid,profile`），逗号分隔 |
| `token_endpoint_auth_method` | varchar(32) | 换 token 鉴权方式：`none`（内部免密钥）/ `client_secret_post` / `client_secret_basic` |
| `status` | varchar(16) | `ACTIVE` / `DISABLED` |
| `created_at` / `updated_at` | datetime | 审计字段（`BaseEntity` 体系） |

**`fm_sso_auth_code`（授权码，短时效，Redis 为主、DB 兜底可选）** → 实体 `SsoAuthCode`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `code` | varchar(64) | 授权码，唯一索引 |
| `app_id` | varchar(64) | 关联应用 |
| `user_id` | bigint | 登录用户 |
| `scopes` | varchar(256) | 本次授权范围 |
| `redirect_uri` | varchar(512) | 回调地址（发放时校验过的） |
| `expires_at` | datetime | 短时效（默认 60s，一次性） |
| `used` | tinyint | 是否已使用（防重放） |
| `created_at` | datetime | |

**`fm_sso_user`（SSO 自带用户库）** → 实体 `SsoUser`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `account` | varchar(64) | 登录账号，唯一 |
| `password` | varchar(128) | BCrypt 加密（`me.auth.bcrypt-strength` 默认 12） |
| `name` | varchar(64) | 用户名 |
| `status` | varchar(16) | `ACTIVE` / `DISABLED` |
| `roles` | varchar(256) | 角色码，逗号分隔（sa-token `StpInterface` 读） |
| `created_at` / `updated_at` | datetime | |

#### 内部 vs 外部的流程差异

**相同部分**（统一授权码流程）：

```
三方应用 → GET /api/sso/authorize?app_id=X&redirect_uri=Y&scope=Z
  → 未登录重定向 /api/sso/login-page
  → 用户登录（fm_sso_user 校验密码）
  → 生成 code，重定向 redirect_uri?code=xxx
三方应用 → POST /api/sso/token  { code, app_id, [app_secret], redirect_uri }
  → 校验 code → 校验 app → 签发 JWT token
```

**差异点**（在 `/api/sso/token` 换 token 时）：

| 校验项 | INTERNAL | EXTERNAL |
|---|---|---|
| `app_id` 存在且 ACTIVE | ✅ | ✅ |
| `app_secret` 验证 | ❌ 跳过（app_secret 为 null） | ✅ 必须匹配 |
| `redirect_uri` 白名单 | ✅ | ✅ |
| `code` 一次性 + 未过期 | ✅ | ✅ |

`token_endpoint_auth_method=none` 的 INTERNAL 应用，换 token 时不验密钥——网络层可信兜底（K8s svc 内网），符合"不走注册分配 aksk 的形式"。

#### 管理接口（SSO 自身，sa-token `@SaCheckRole("admin")` 保护）

- `POST /api/sso/admin/app` — 注册应用（INTERNAL 默认不发 secret，EXTERNAL 生成并加密存储 + 通知）
- `PUT /api/sso/admin/app/{appId}` — 更新应用（回调白名单、scope、状态）
- `POST /api/sso/admin/app/{appId}/reset-secret` — 重置 EXTERNAL 应用密钥（INTERNAL 无此操作）
- `DELETE /api/sso/admin/app/{appId}` — 禁用应用
- `GET /api/sso/apps` — 应用列表
- `POST /api/sso/admin/user/{userId}/logout` — 强制登出（调 `StpUtil.logout(userId)`，踢人 + 发事件）

这些管理动作通过 `frame-me-starter-op-audit` 桥接审计。

### Token 体系与端点设计

#### Token 体系

**签发侧（SSO）**：RS256 非对称，SSO 持私钥签发。复用 sa-token JWT Simple 模式（`StpLogicJwtForSimple`），签名算法从默认 HS256 切成 RS256。

- 私钥配置项：`me.auth.sa-token.jwt.private-key`（PEM 格式或 Base64，走 `sensi-encrypt` 加密存储）
- 公钥配置项：`me.auth.sa-token.jwt.public-key`（下游应用拿这个验签）
- 启动期 fail-fast 校验：私钥缺失/格式非法 → 抛异常（复用 `SaTokenJwtAutoConfiguration.validateJwtSecret()` 的模式，扩展为非对称校验）
- 密钥可插拔：签名器抽象成 `IJwtSigner` 接口，现期 RS256 实现，将来换 ES256 或支持多密钥轮转（JWKS）只加实现类

**JWT Claims 约定**（与将来 OIDC id_token 字段对齐）：

| claim | 含义 | 示例 |
|---|---|---|
| `sub` | 用户 ID（userId） | `1001` |
| `iss` | 签发方，固定 SSO 标识 | `frame-me-sso` |
| `aud` | 应用 appId（受众） | `fm-internal-order` |
| `exp` | 过期时间 | - |
| `iat` | 签发时间 | - |
| `scope` | 授权范围 | `openid,profile` |
| `nonce` | 防重放（三方传入，原样回填） | 可选 |

下游应用验签后从 `sub` 拿 userId，从 `aud` 确认是给自己的 token，从 `scope` 做鉴权。

**会话元数据**（SSO 自用 Redis）：token 是 JWT（自验签），但 sa-token 会话数据（Account-Session 用户快照、在线会话列表）仍在 SSO 的 Redis——供踢人/强制登出/在线管理。下游不碰这个 Redis。

#### 踢人机制（分层，SSO 侧提供三种能力，下游按需选）

| 档位 | 机制 | 下游依赖 | 适用 |
|---|---|---|---|
| 档1（默认） | JWT 短时效（access 默认 2h）+ refresh 时 SSO 拒绝已注销会话 | 零 Redis | 内部应用默认 |
| 档2（可选增强） | 下游 multi-redis 某命名实例指向 SSO Redis，查 token 黑名单 | multi-redis 指向 SSO Redis | 需强一致的内部应用 |
| 档3（事件通知） | SSO 踢人时发 `UserLogoutEvent`，下游订阅后自行处理 | 下游自选 transport 订阅 | 无法直连 SSO Redis 的外部三方 |

三档**共存不互斥**：SSO 侧同时提供"短时效 + 黑名单查询 + 事件发送"三种能力，下游按自己情况选一种或组合用。

- 档1：下游只验 JWT 签名 + 过期，**完全不连 Redis**。踢人靠 SSO 侧 `refresh` 时拒绝（已踢人会话在 SSO Redis 已清，refresh 查不到 → 拒绝续期）。延迟窗口 = access TTL。
- 档2：下游引 multi-redis，**其中一个命名实例**（如 `@RedisClient("sso")`）指向 SSO 的 Redis，查黑名单时用这个实例。下游业务 Redis 用另一个实例，互不干扰。
- 档3：SSO 的 `SsoAuthService.logout(userId)` / `logoutByUserId(userId)` 执行 `StpUtil.logout(userId)` 后，**额外发一个 `UserLogoutEvent`**。事件内容：`userId`、`appId`（可选）、`logoutTime`、`reason`。transport 可选（Redis pub/sub、webhook、op-audit 事件桥），是 SSO 的能力出口——外部三方无法直连 SSO Redis（档2 用不了），就靠这个事件感知踢人。下游订阅方式（应用自己选）：收到事件写本地黑名单 / 清本地用户缓存 / 仅记日志 / 不订阅（等同档1）。

#### 端点设计

**对外端点**（授权码流程，`me.sso.path` 默认 `/api/sso`）：

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/authorize` | `@Anonymous` | 授权端点。校验 app_id/redirect_uri/scope → 未登录重定向登录页 → 已登录直接发 code 回调 |
| GET | `/login-page` | `@Anonymous` | 返回登录页 HTML（可配 `me.sso.login-page.enabled=false` 切纯 JSON） |
| POST | `/login` | `@Anonymous` | 账号密码登录，校验 `fm_sso_user`，建立 sa-token 会话 |
| POST | `/token` | `@Anonymous` | 换 token。校验 code + app_id +（INTERNAL 跳过 / EXTERNAL 验 app_secret）+ redirect_uri → 签发 JWT |
| POST | `/refresh` | `@Anonymous` | refresh 续期（sa-token `renewTimeout` 或签发新 JWT） |
| GET | `/logout` | 登录态 | 登出当前会话 |
| GET | `/jwks` | `@Anonymous` | 公钥 JWKS 端点（演进 OIDC 用，首期可暴露单公钥） |

**管理端点**（`/api/sso/admin/**`，`@SaCheckRole("admin")`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/app` | 注册应用 |
| PUT | `/admin/app/{appId}` | 更新应用 |
| POST | `/admin/app/{appId}/reset-secret` | 重置 EXTERNAL 密钥 |
| DELETE | `/admin/app/{appId}` | 禁用应用 |
| GET | `/admin/apps` | 应用列表 |
| POST | `/admin/user/{userId}/logout` | 强制登出 |

#### 登录页策略

默认内置简单登录页（`GET /api/sso/login-page` 返回 HTML 表单，POST 到 `/api/sso/login`）。通过 `me.sso.login-page.enabled` 控制：

- `true`（默认）：返回 HTML 页面，适合三方重定向过来直接展示。
- `false`：只暴露 `/api/sso/login` JSON 接口，前端自行渲染。

## 配置约定

```yaml
me:
  sso:
    enabled: true                          # SSO 服务总开关
    path: /api/sso                         # 端点基础路径
    table-prefix: fm_                      # 表前缀，默认 fm_
    login-page:
      enabled: true                        # 默认提供 HTML 登录页
    auth-code:
      expires: 60s                         # 授权码时效，默认 60s，一次性
    token:
      access-expires: PT2H                 # access token 时效
      refresh-expires: P7D                 # refresh 时效
    jwt:
      private-key: ME(密文)                # RS256 私钥，走 sensi-encrypt
      public-key: classpath:sso-public.pem # 公钥（下游也配这个验签）
      issuer: frame-me-sso                 # 签发方标识
  auth:
    sa-token:
      jwt:
        enabled: true                     # 启用 sa-token JWT 模式（SSO 侧必须 true）
      redis:
        enabled: true                     # SSO 侧 Redis 会话
        client-name: sso                   # 命名实例，指向 SSO 自用 Redis
```

敏感值分类处理：

| 配置项 | 敏感性 | 处理 |
|---|---|---|
| `me.sso.jwt.private-key` | 极高 | `ME(密文)` 加密，走 sensi-encrypt |
| `me.sso.jwt.public-key` | 低 | 明文或 classpath 资源 |
| app_secret（DB 存储） | 高 | sensi-encrypt 加密后入库 |
| `me.auth.sa-token.jwt-secret-key` | 高 | 若 sa-token 原生 HS256 模式启用才需（本设计走 RS256，不启用） |

## 演进路径（自研轻量 SSO → 标准 OIDC）

方案1的底座在演进 OIDC 时可完全复用，OIDC 只是**叠加标准端点和非对称签名**，不推翻核心：

| 阶段 | 现期（本设计） | 将来补 OIDC |
|---|---|---|
| 应用注册表 | `fm_sso_app`（appId/secret/redirect_uris/scope） | 复用，client_metadata 字段对齐 OIDC client 注册 |
| 授权码流程 | `/authorize` + `/token`（code 换 token） | 复用，补标准 `grant_type=authorization_code` 参数 |
| token 签名 | RS256 JWT，`IJwtSigner` 可插拔 | 复用，密钥轮转加 `/.well-known/jwks.json` JWKS 端点 |
| id_token | 不签发（只签 access token） | 新增签发 `id_token`（含 sub/iss/aud/exp/nonce），复用 claims 结构 |
| discovery | 无 | 新增 `/.well-known/openid-configuration` 只读端点 |
| userinfo | 无 | 新增 `/userinfo` 只读端点，复用 `IAuthUserDetailsService` |
| PKCE | 不强制 | 扩展 `/authorize` 支持 `code_challenge`/`code_verifier` |
| client 认证 | INTERNAL 免密钥 / EXTERNAL client_secret | 复用，补 `private_key_jwt`/`none` 等标准方法 |

**设计文档预留的扩展点**（不实现，标注 TODO）：

1. `IJwtSigner` 接口——现期 RS256 单实现，将来加 JWKS 多密钥轮转实现。
2. `me.sso.oidc.enabled`（默认 false）——将来置 true 激活 discovery/userinfo/id_token 端点。
3. token claims 结构按 OIDC id_token 字段命名——演进时 id_token 直接复用。
4. 应用注册表的 `token_endpoint_auth_method` 用 OIDC 标准枚举值——对齐标准，将来不重命名字段。

## 测试策略

| 测试类 | 测试要点 |
|---|---|
| `SsoContextLoadTest` | H2 + 最小配置验证 Spring 上下文加载（参照现有 `EventBridgeAutoConfigurationTest` 模式） |
| `SsoAuthServiceTest` | 授权码签发/换 token 流程；INTERNAL 免密钥/EXTERNAL 验密钥；code 一次性防重放；redirect_uri 白名单 |
| `SsoAppServiceTest` | 应用注册/更新/禁用；EXTERNAL 密钥加密存储；INTERNAL 不发 secret |
| `SsoAuthControllerTest` | `/authorize` 未登录重定向/已登录发 code；`/token` 换 token；`/login` 登录建会话 |
| `SsoAdminAuthorizeTest` | 管理端点 `@SaCheckRole("admin")` 鉴权；非 admin 拒绝 |
| `JwtSignerTest` | RS256 签发/验签；私钥缺失 fail-fast；公钥验签通过 |
| `UserLogoutEventTest` | 踢人触发事件；事件内容正确 |

集成测试（Testcontainers + MySQL）可选，验证 MyBatis-Flex 表结构与授权码/用户库的实际读写。

## 文档同步

实现完成后需更新：

| 文档 | 更新内容 |
|---|---|
| `docs/modules.md` | 新增 `frame-me-sso` 章节职责描述、关键类、可配置项 |
| `docs/reference.md` | 关键类索引补充 SSO 相关类路径；扩展点补充 SSO |
| `docs/architecture.md` | 服务边界补充 SSO 认证服务与下游应用的调用关系 |
| `docs/conventions.md` | 补充 SSO 表前缀约定（`fm_sso_*`）、claims 约定 |
| `docs/guides/sso.md`（新建） | 专题：SSO 接入指南（内部/外部应用如何接入、下游如何验签） |
| `CLAUDE.md` | 最常用命令补充 SSO 服务启动命令 |

## 不做的事（YAGNI）

| 排除项 | 理由 |
|---|---|
| 注册发现/配置中心（cloud/cloud-nacos） | 首期不接，SSO 地址配置注入，下游通过配置的 svc 地址访问 |
| Spring Authorization Server | 现期不引标准 OP 框架，自研轻量流程够用，演进时再加 |
| sa-token-sso 官方私有协议 | 私有客户端协议成为演进 OIDC 的包袱，排除 |
| auth-rbac 模块 | SSO 自身管理端点鉴权用 sa-token 原生 `@SaCheckRole` 替代 |
| 多密钥轮转 / JWKS 动态端点首期实现 | 现期单 RSA 密钥对 + 配置文件，JWKS 端点留作演进项 |
| refresh_token 标准格式 | 首期用 sa-token `renewTimeout` 续期原 token，标准 refresh_token 留作演进项 |
| 前端工程 | 首期内置简单 HTML 登录页，不单独搞前端工程 |

## 风险与边界

1. **RS256 密钥管理**：私钥泄露=可伪造所有 token。走 sensi-encrypt 加密存储，生产建议挂 K8s Secret。密钥轮转首期不实现，文档标注升级路径。
2. **内部应用免密钥的安全性**：依赖网络层可信（K8s 内网），若内网被突破可伪造 app_id 换 token。文档标注边界——内部应用必须部署在可信网络。
3. **短时效踢人延迟**：档1默认有最多 access TTL 延迟，对安全敏感场景必须显式开档2或档3。
4. **授权码重放**：code 一次性 + 短时效（60s）+ `used` 标志防重放，Redis 原子操作保证并发安全。
5. **redirect_uri 校验**：严格白名单匹配，防止开放重定向漏洞（EXTERNAL 应用必须精确匹配，INTERNAL 可配通配）。

## 实现步骤（转入 writing-plans 时细化）

1. `frame-me-sso/pom.xml` 补全依赖（auth-sa-token/mybatis-flex/multi-redis/sensi-encrypt/op-audit/msg-notify/doc-openapi）
2. 新建主启动类 `SsoApplication`、`application.yml`（业务端口/management 端口分离/数据源/multi-redis/sa-token-jwt RS256 配置）
3. 实体 + Mapper + Service：`SsoApp`/`SsoAuthCode`/`SsoUser` + 对应 Flex Mapper + Service
4. `IJwtSigner` 接口 + `Rs256JwtSigner` 实现 + 启动期 fail-fast 校验
5. `SsoAuthService implements IAuthService`（授权码校验 → `StpUtil.login` → 签发 JWT）
6. `SsoAuthController`（authorize/login-page/login/token/refresh/logout/jwks）
7. `SsoAdminController`（app 注册/更新/重置密钥/禁用/列表 + 强制登出）+ `@SaCheckRole("admin")` 鉴权
8. `UserLogoutEvent` + 事件发送（logout 时触发）
9. 内置登录页 HTML 资源 + `me.sso.login-page.enabled` 配置开关
10. 测试类（上下文加载/授权码流程/换 token/JWT 签发验签/踢人事件）
11. 文档同步（modules/reference/architecture/conventions/guides/CLAUDE.md）

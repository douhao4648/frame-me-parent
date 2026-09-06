# 网关（frame-me-gateway）指南

> 本文档说明 `frame-me-launcher/frame-me-gateway` 业务网关的定位、鉴权模型、身份头契约、部署拓扑与优雅上下线。

## 定位：薄业务网关 + 可向上让渡

自建网关是**业务网关**：懂本工程的鉴权体系与身份头约定。它与云厂商流量网关（如阿里云 MSE）不冲突——MSE 挡流量（TLS/WAF/限流），本网关做业务路由与鉴权，两层并存；不上云时本网关是唯一入口。

**功能可让渡**：上 MSE 的环境可把认证/限流上移（MSE 内置 JWT 认证或远端鉴权回调），自建网关退化为纯 `lb://` 路由甚至摘掉。**不变量是身份头契约**（见下）——自建网关与 MSE 插件都是该契约的实现者。

## 技术栈与依赖边界

Spring Cloud Gateway（WebFlux，`spring-cloud-starter-gateway-server-webflux`，SC 2025.1.2 BOM 管控）。

**依赖红线**：网关**不引** `frame-me-boot` / `frame-me-starter-base` / `frame-me-starter-auth*` / `frame-me-starter-multi-redis` / sa-token——它们经 base 拖入 `spring-boot-starter-web`（Servlet 栈），与 SC Gateway WebFlux 同 classpath 直接启动失败。网关侧不引用也不复用这些模块的类（如 `JwtTokenServiceImpl`/`RedisSaTokenDao`），对应能力在网关内以 web 无关方式重写，约定以防漂移测试与本文档维护。

反向同样成立：共享 starter（含 `frame-me-starter-cloud`）不声明任何 web 栈硬依赖，Servlet 应用不会因本工程支持 WebFlux 网关而拿到 flux jar。

## 鉴权模型：凭证驱动 + 体系级用户验证器开关

### 凭证驱动分发（不按路由配类型）

user / app 两种凭证对**所有**下游服务自由可用——一部分接口是用户登录访问，另一部分是二三方应用 HMAC 访问，针对的是所有后面的服务而非特定路由，故**没有按路由声明鉴权类型的概念**，`GatewayAuthFilter` 按请求携带的凭证自动识别（协议层可区分，无歧义）：

```
白名单路径              → 剥离身份头后放行
Authorization: "Signature " 前缀 → app 验签（显式 app 意图，优先于用户凭证；验签失败 401，不落回）
否则提取到用户 token    → user 校验（失败 401，不落回）
无任何凭证              → 按 me.gateway.auth.allow-anonymous：false（默认）= 401 要求认证；true = 剥离身份头后匿名放行
```

| 分支 | 网管动作 |
|---|---|
| app（`Authorization: Signature`） | HMAC 签名验证（见下）→ 剥离身份头 → 注入 `X-App-Key` |
| user（Bearer / tokenName 头） | 校验用户 token → 剥离外部身份头 → 注入 `X-User-Id`（及 `X-User-Account`） |
| 无凭证 + `allow-anonymous: true` | 仅剥离外部身份头后放行（防伪造是底线，不是可选项；仅 internal 实例允许） |

- **优先级**：sa-token 模式下 tokenName 头与 `Authorization: Signature` 并存时走 app（Signature 是显式意图）。
- `me.gateway.auth.whitelist`（Ant 风格路径白名单，如 `/api/auth/sso-login`）：命中跳过鉴权但**仍剥离身份头**。

### 实例级认证器开关（拓扑自由）

```yaml
me:
  gateway:
    auth:
      user-auth-enabled: true   # false = 本实例不识别用户凭证（携带也 401）
      app-auth-enabled: true    # false = 本实例不识别 Signature 凭证（携带也 401）
```

默认单实例双凭证。将来需要隔离/独立伸缩时，**拆实例是纯部署动作、代码零改动**：例如起一个 `app-auth-enabled: true` + `user-auth-enabled: false` 的实例即为 app 专属网关（user 验证器不装配，sa-token 模式也不再要求 Redis）。

### 用户 token 验证器：`user-validator` 二选一

体系内所有下游统一认证底座，不存在混合拓扑，故开关在应用级（`me.gateway.auth.user-validator`），经 Nacos 配置中心改配置重启即切换：

| 值 | 下游底座 | 网关行为 | 吊销即时性 |
|---|---|---|---|
| `jwt`（默认） | `frame-me-starter-auth-jwt` | jjwt 本地验签（HS256 共享密钥 + issuer + `type=access`），无状态 | 踢人后 refresh 失效，已签发 access token 自然过期（窗口 = 下游 `me.auth.jwt.access-token-expires`） |
| `sa-token` | `frame-me-starter-auth-sa-token` | `ReactiveStringRedisTemplate`（Lettuce reactive，全链路非阻塞）直查 `{tokenName}:{logicType}:token:{token}`（sa-token 原生 key 规则） | 踢人删 key，网关即时 401 |

- **jwt 模式**：`me.gateway.auth.jwt.secret`（≥256 位）与 `issuer` 必须与下游 `me.auth.jwt.*` 一致，由配置中心统一下发；运行期零 Redis 依赖（lettuce 不连接，`management.health.redis.enabled=false` 防健康检查拖 DOWN）。
- **sa-token 模式**：网关与下游共用**同一 Redis 实例**（有意不支持多 Redis——multi-redis 依赖 base 会拖入 Servlet 栈）；需配置下游的 `token-name`/`logic-type`；选中但无 `spring.data.redis` 配置 → 启动 fail-fast。
- 不模拟 sa-token 的 active-timeout 滑动语义：网关只做粗筛，精确会话治理归下游。

### app 签名契约（对齐 APISIX hmac-auth / draft-cavage）

请求头：

| 头 | 内容 |
|---|---|
| `Authorization` | `Signature keyId="<appKey>",algorithm="hmac-sha256",headers="date @request-target",signature="<base64>"` |
| `Date` | HTTP GMT 日期（RFC 1123），与网关时钟偏差 >300 秒拒绝（对齐 APISIX `clock_skew` 默认值，防重放） |

待签串（`\n` 拼接，尾随 `\n`）：`keyId` 为首行，随后按 `headers` 声明顺序逐行 `头名: 值`（头名保留声明的原样大小写），`@request-target` 展开为 `METHOD request-uri`（大写方法，raw path + query string，对齐 APISIX `request_uri`）。固定顺序下即：

```
<appKey>\n
date: <Date 头值>\n
GET /api/data?x=1\n
```

签名 = `base64(HmacSHA256(secret, 待签串))`，与 APISIX 一致比较原始 HMAC 字节（本网关用常量时间比较防时序侧信道）。

与 APISIX 的三处有意识收窄/增强：仅支持 `hmac-sha256`（APISIX 默认还允许 sha1/sha512）；强制 `headers` 覆盖 `date` + `@request-target`（防降级——缺 date 即无防重放）；常量时间比较（APISIX 为直接相等）。**收益：未来 app 鉴权让渡到 APISIX/MSE 时客户端契约不变**（仅需补齐收窄项）。

凭证经配置中心下发（`me.gateway.auth.apps[]`），secret 支持 sensi-encrypt 密文。客户端可用 `ConfigAppAuthenticator.httpDate()` / `sign(...)` / `authorizationHeader(...)` 同一约定构造请求。

## 身份头契约（不变量）

- 外部请求携带的 `X-User-Id` / `X-User-Account` / `X-App-Key` **一律在网关剥离**，任何分发分支不例外；
- 认证通过后由网关重新注入：user → `X-User-Id`（+`X-User-Account`）；app → `X-App-Key`；
- **下游两种形态均为一等拓扑**：装体系认证底座（网关粗筛 + 下游自验，双保险，有完整会话治理），或不装底座开 `trusted-header`（认证卸载到网关，薄服务首选；体系切换认证底座时此类下游零改动）；
- **模式不对称**：`jwt` 模式 claims 自带 account，两个头都注入；`sa-token` 模式 token key 的 value 只有 loginId，**只注入 `X-User-Id`**。sa-token 体系下纯 trusted-header 下游 `AuthContext.account=null`（沿内网传播链同样为 null）；装 auth-sa-token 的下游不受影响（自身 resolver 解析透传 token 拿全量 User）。需要 account 的 trusted-header 下游可开启回源补全：`me.auth.trusted-header.fetch-details=true` + 实现 `IAuthUserDetailsService`（按 `X-User-Id` 调 `loadUserById` 补全完整用户并校验禁用状态），或自定义 `IAuthUserResolver`；
- 下游服务以 `me.auth.trusted-header.enabled=true` 信任身份头——**前提**是网关剥离 + 内网隔离（下游不直接对公网暴露）。

## 错误响应格式

网关自身产生的错误一律与 base `IResult` 同构（`{code, msg, rid}`，Boot 4 Jackson 省略 null 字段，与下游一致），不出 Boot 默认的 `{timestamp, path, status, error}` 结构：

- `GatewayGlobalExceptionHandler` 接管未捕获异常与未命中路由的 404，语义对齐 base `GlobalExceptionHandler` 兜底分支：`ErrorResponse` 请求侧异常（404 `NoResourceFoundException` 等）透传状态码与请求级 message；未知异常对外掩码为 500「系统错误」，真实堆栈只进服务端日志；`rid` 取 WebFlux 请求 ID 便于排障关联；
- **必须 `@Order(-2)`**：装配虽顶掉 Boot 默认处理器（`@Order(-1)`），但 Spring 的 `ResponseStatusExceptionHandler` 仍在异常链中，不显式靠前则 404 被它先消费成空 body；
- 鉴权过滤器的 401 复用同一写出逻辑（`GatewayGlobalExceptionHandler.writeError`），下游客户端只需解析一种错误结构。

下游服务返回体原样透传，网关不改写。

## 部署拓扑：一份代码 + 拓扑 profile

内外网隔离用同一份 artifact、不同激活 profile 部署：

- **`public`** 实例（对公网）：`application-public.yml` 显式钉死 `allow-anonymous: false`。建议关闭 discovery locator，显式路由白名单化。
- **`internal`** 实例（内网）：激活 `internal` profile 名 + `allow-anonymous: true`（无凭证匿名放行，携带合法凭证的请求仍正常认证注入身份头——"可选认证"形态），可开 discovery locator。

**fail-closed 守卫**：`AnonymousAccessGuard` 启动期强制——`allow-anonymous=true` **仅在显式激活 `internal` profile 时合法**，其余一律拒绝启动（含 public、含忘记配置拓扑 profile）。方向是"匿名放行必须依附内网声明"，而不是"公网自觉声明 public"：公网部署忘记配 profile 时匿名配置不会静默生效。守卫默认开启，明确知晓风险时可显式置 `me.gateway.auth.anonymous-guard-enabled=false` 关闭（放弃防呆保护）。

> 两个认证器开关与 `allow-anonymous` 是正交维度：开关管"携带该类凭证认不认"（关闭即 401，fail-closed），`allow-anonymous` 管"无凭证怎么办"。"双关 + allow-anonymous=false" 不是功能模式而是事故的 fail-closed 着陆区（误配双关 → 全 401 而非裸奔）；"内部畅行"需显式声明（双关 + `allow-anonymous: true` + `internal` profile）。

## 优雅上下线

直接复用 `frame-me-starter-cloud` 编排（health DOWN → 反注册 → 等待消费者刷新 → SIGTERM 兜底）。网关作为流量入口无特殊逻辑——前置 LB / Nacos 消费者在 `deregister-wait` 窗口内停止导流。

preStop 钩子（token 走路径段，双栈通用）：

```bash
curl -X POST http://localhost:10031/actuator/offline/{token} -H 'Content-Length: 0'
```

注意 K8s `terminationGracePeriodSeconds` ≥ `deregister-wait`(15s) + Spring 关闭耗时（建议 60s）。

## 配置参考

```yaml
me:
  gateway:
    auth:
      user-auth-enabled: true        # 实例级开关：false = 不识别用户凭证（携带也 401）
      app-auth-enabled: true         # 实例级开关：false = 不识别 Signature 凭证
      user-validator: jwt            # jwt | sa-token
      jwt:
        secret: <与下游 me.auth.jwt.secret 同一把，≥256 位>
        issuer: me
      sa-token:
        token-name: satoken          # sa-token 模式：与下游一致
        logic-type: login
      whitelist:                     # 匿名白名单（仍剥离身份头）
        - /api/auth/sso-login
      allow-anonymous: ${GATEWAY_ALLOW_ANONYMOUS:false}  # 无凭证请求：false=401（默认）| true=匿名放行（public 禁 true）；K8s 多实例用环境变量覆盖
      anonymous-guard-enabled: true      # 匿名放行守卫（默认开）：true 时 allow-anonymous=true 必须激活 internal profile，否则拒绝启动
      apps:                          # app 签名凭证
        - app-key: fm-external-demo
          secret: ME(密文)
    access-log:                      # 访问日志（WebFilter 最外层，未命中路由的 404 也留痕）
      enabled: false                 # 默认关闭；true 才装配过滤器，零开销
      max-length: 2000               # URI（含 query）超长截断补 ...，0 不限
```

换注册中心：`frame-me-starter-cloud-nacos` 平替为其他 discovery starter 即可，`lb://` 路由与注册中心实现解耦。

已知噪音（Nacos 配置热刷新）：编辑 Nacos 配置触发 refresh 时，`ConfigurationPropertiesRebinder` 会对 SC Gateway 5.0 的 `RequestRateLimiterGatewayFilterFactory` / `RedisRateLimiter` 打 WARN 堆栈（`NoSuchMethodException: <init>`——这两个 `@ConfigurationProperties` 类只有构造器注入，无无参构造器，rebinder 无法实例化默认实例做 reset diff）。无害：WARN 后跳过 reset 继续 rebind，刷新以 `[notify-ok]` 完整成功。已在 gateway `application.yml` 将该 logger 压到 `ERROR`；未来升级 spring-cloud 若上游修复可移除。

注意（SCA 2025.1.0.0 前缀半迁移，实机验证）：import-check 走新前缀——classpath 有 nacos-config starter 就要么 `spring.config.import: optional:nacos:<dataId>`，要么 `spring.nacos.config.import-check.enabled: false`，否则启动硬失败；但 **config 客户端的连接参数（server-addr/access-key/secret-key/group/file-extension）仍绑定旧前缀 `spring.cloud.nacos.config`**，写到新前缀会被静默忽略、回落默认 127.0.0.1:8848；discovery 侧维持 `spring.cloud.nacos.discovery.*`。config 与 discovery 是两套独立 properties / 两个 nacos-client 实例，凭证无共享机制，需各配一份。URL 参数 `refreshEnabled` 缺省走总开关 `spring.nacos.config.refresh-enabled`（默认 true），显式写 `=true` 是冗余。

## 演进

- 网关层对 JWT 的即时踢人：订阅档3 `UserLogoutEvent` 维护本地黑名单（需引 Redis pub/sub），本次未做；
- OIDC/RS256：SSO 演进路 3 换非对称签名后，网关改验公钥（JWKS），HMAC 共享密钥约定下线。

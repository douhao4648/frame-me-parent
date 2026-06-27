# 模块速查

## `frame-me-api`

- **定位**：纯接口 / Interfacer 契约模块，禁止引入 Spring 依赖。
- **依赖**：无。
- **关键类**：
  - `com.frame.me.api.result.IResult<T>` — 统一响应结果接口。
  - `com.frame.me.api.result.PageData<T>` — 通用分页结果。
  - `com.frame.me.api.query.PageQuery` — 通用分页查询参数。
  - `com.frame.me.validation.CreateGroup` — 校验分组：新增场景。
  - `com.frame.me.validation.UpdateGroup` — 校验分组：更新场景。
  - `com.frame.me.validation.annotation.TimeRange` — 类级时间范围校验注解。
  - `com.frame.me.validation.validator.TimeRangeValidator` — `@TimeRange` 校验器实现。
  - `com.frame.me.api.ApiConstant` — 占位常量接口。
- **使用方**：业务工程的 `xx-api` 模块。
- **设计约定**：
  - 业务 `xx-api` 通过引入 `frame-me-api` 获得统一的接口契约与校验分组。
  - 业务 `xx-api` 之间可以相互引用，用于跨业务接口调用。
  - `frame-me-api` 不实现任何具体能力，只定义最基础的跨模块接口、常量、分页模型与校验契约。

## `frame-me-starter-base`

- **定位**：Spring Web 基础设施与 MyBatis-Plus 数据访问模块。
- **依赖**：`frame-me-api`、`spring-boot-starter-web`、`mybatis-plus-spring-boot4-starter`、`spring-boot-starter-jdbc`、`mysql-connector-j`、`hutool-all`、`lombok`。
- **关键类**：
  - `com.frame.me.base.advice.GlobalExceptionHandler` — 全局异常处理。
  - `com.frame.me.base.config.BaseAutoConfiguration` — 自动装配入口。
  - `com.frame.me.base.env.EnvironmentHelper` — 获取 Spring active profile、判断当前环境（dev/test/prod/daily/pre）。
    - 提供 `getActiveProfiles()`、`getActiveProfile()`、`isProfileActive(String)`、`isDev()`、`isTest()`、`isProd()`、`isDaily()`、`isPre()` 等方法。
  - `com.frame.me.base.result.ResultCode` — 状态码枚举。
  - `com.frame.me.base.result.Result<T>` — `IResult<T>` 默认实现，并提供静态工厂方法。
  - `com.frame.me.base.exception.BusinessException` — 业务异常。
  - `com.frame.me.base.exception.InternalException` — 内部异常。
  - `com.frame.me.base.exception.RetryException` — 重试异常。
  - `com.frame.me.base.BaseConstant` — 占位常量接口。
  - `com.frame.me.base.mybatis.entity.BaseEntity` — 基础实体，含 `id`（雪花算法）、`createTime`、`updateTime`、`deleted`。
  - `com.frame.me.base.mybatis.entity.BaseVersionEntity` — 继承 `BaseEntity`，额外提供 `version`（乐观锁）。
  - `com.frame.me.base.mybatis.plugin.BaseMetaObjectHandler` — 公共字段自动填充，需通过 `frame.me.mybatis.meta-object-handler.enabled=true` 开启。
  - `com.frame.me.base.mybatis.util.PageUtils` — 新规范分页工具，`PageQuery` / `PageData` 与 MyBatis-Plus `Page` 转换。
  - `com.frame.me.base.mybatis.util.SnowflakeUtils` — 基于 Spring 容器获取 `IdentifierGenerator` 生成雪花 ID。
  - `com.frame.me.base.mybatis.config.MybatisPlusProperties` — `frame.me.mybatis` 配置属性绑定。
  - `com.frame.me.base.mybatis.config.MybatisPlusConfiguration` — 分页插件、乐观锁插件、公共字段自动填充处理器以及可选的自定义 ID 生成器注册。
- **自动装配**：通过 `frame-me-starter-base/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `BaseAutoConfiguration`、`MybatisPlusConfiguration`。
- **可配置项**：
  - `frame.me.mybatis.meta-object-handler.enabled` — 是否启用公共字段自动填充，默认 `false`。
  - `frame.me.mybatis.snowflake.worker-id` — 雪花算法 workerId，未配置时使用 MyBatis-Plus 默认推导值。
  - `frame.me.mybatis.snowflake.datacenter-id` — 雪花算法 datacenterId，默认 `0`。
- **Maven Profile**：
  - `p6spy` — 引入 `p6spy-spring-boot-starter`，用于 SQL 监控：`mvn ... -Pp6spy`。
  - `swagger` — 引入 `frame-me-starter-doc-openapi`，用于接口文档：`mvn ... -Pswagger`。
- **设计约定**：
  - 表名到实体名映射：去掉第一个下划线前缀，例如 `spo_fms_device` → `FmsDevice`。
  - **Mapper 接口必须标注 `@Mapper` 注解**，并继承 MyBatis-Plus `BaseMapper<T>`，以便自动扫描与通用 CRUD。
- **扩展提示**：与 Spring Web 相关的基础能力（拦截器、参数解析器、统一日志等）适合放在这里。

## `frame-me-adapter`

- **定位**：适配层聚合模块（`pom` 打包），承载老接口规范的契约与适配能力。拆分为 `frame-me-adapter-api`（契约）与 `frame-me-adapter-starter`（实现）。**凡集成 `frame-me-adapter-starter` 的项目即表示遵循老接口规范**。
- **子模块**：

### `frame-me-adapter-api`

- **定位**：老规范契约模块，集成 `frame-me-api`，仅含对外契约类，不含 Spring 自动装配。
- **依赖**：`frame-me-api`、`lombok`。
- **关键类**：
  - `com.frame.me.adapter.api.query.PageParam` — 老规范分页请求参数（`pageNum`/`pageSize`/`searchCount`/`orders`）。
  - `com.frame.me.adapter.api.result.PageResult<T>` — 老规范分页结果（`pageNum`/`pageSize`/`total`/`pages`/`list`）。

### `frame-me-adapter-starter`

- **定位**：内部 `IResult<T>` 与外部 `Response<T>` 的适配层，并提供老规范分页工具。
- **依赖**：`frame-me-adapter-api`、`frame-me-starter-base`、`lombok`。
- **关键类**：
  - `com.frame.me.adapter.advice.Result2ResponseAdvice` — `ResponseBodyAdvice`，将 `IResult<T>` 转为 `Response<T>`。
  - `com.frame.me.adapter.result.Response<T>` — 外部响应结构。
  - `com.frame.me.adapter.result.ResponseJacksonModule` — 将 `IResult` 抽象类型映射为 `Response` 的 Jackson 模块。
  - `com.frame.me.adapter.mybatis.util.PageableUtils` — 老规范分页工具，`PageParam` / `PageResult` 与 MyBatis-Plus `Page` 转换。
  - `com.frame.me.adapter.config.AdapterAutoConfiguration` — 自动装配入口。
  - `com.frame.me.adapter.AdapterConstant` — 占位常量接口。
- **自动装配**：通过 `frame-me-adapter/frame-me-adapter-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `AdapterAutoConfiguration`。
- **扩展提示**：与外部协议相关的转换（如 OpenFeign 适配、DTO 转换、字段脱敏等）适合放在这里。

## `frame-me-starter-dynamic-ds`

- **定位**：多数据源 starter，基于 baomidou `dynamic-datasource-spring-boot4-starter`。
- **依赖**：`frame-me-starter-base`、`dynamic-datasource-spring-boot4-starter`、`lombok`。
- **关键类**：
  - `com.frame.me.dynamic.ds.config.DynamicDataSourceAutoConfiguration` — 自动装配入口。
  - `com.frame.me.dynamic.ds.provider.MeDynamicDataSourceProvider` — 根据 `spring.datasource.*` 自动创建名为 `master` 的默认数据源。
- **自动装配**：通过 `frame-me-starter-dynamic-ds/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `DynamicDataSourceAutoConfiguration`。
- **启用条件**：
  - 类路径存在 baomidou `DynamicDataSourceAutoConfiguration`。
  - `frame.me.dynamic-datasource.enabled=true`（默认 `true`，可省略）。
  - `spring.datasource.dynamic.enabled=true`（默认 `true`，可省略）。
- **使用方式**：
  - 当存在 `spring.datasource.url` 时，自动创建 `master` 数据源。
  - 若 `spring.datasource.dynamic.datasource` 中也显式配置了 `master`，则显式配置优先级更高，会覆盖自动创建的 `master`。
  - 支持读取 `spring.datasource.hikari.*` 和 `spring.datasource.druid.*` 连接池属性。
  - 需要切换数据源时，使用 `@DS("slave")` 等 baomidou 注解。
- **设计约定**：
  - 已纳入 `frame-me-booter`，业务 `xx-service` 引入 `frame-me-booter` 即可默认获得多数据源能力。

**示例配置**：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/frame_me_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 10
    dynamic:
      hikari:
        maximum-pool-size: 10
        minimum-idle: 10
      datasource:
        second:
          url: jdbc:mysql://localhost:3306/frame_me_test_2?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver
```

## `frame-me-starter-doc-openapi`

- **定位**：接口文档 starter，基于 SpringDoc OpenAPI 3。
- **依赖**：`springdoc-openapi-starter-webmvc-ui`、`lombok`。
- **关键类**：
  - `com.frame.me.doc.openapi.config.DocOpenApiAutoConfiguration` — 自动装配入口。
  - `com.frame.me.doc.openapi.config.DocOpenApiProperties` — `frame.me.swagger` 配置属性绑定。
  - `com.frame.me.doc.openapi.config.GroupedOpenApiRegistrar` — 动态注册 API 分组。
  - `com.frame.me.doc.openapi.DocOpenApiConstant` — 占位常量接口。
- **自动装配**：通过 `frame-me-starter-doc-openapi/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `DocOpenApiAutoConfiguration`。
- **启用条件**：
  - 类路径存在 `io.swagger.v3.oas.models.OpenAPI`。
  - 配置 `frame.me.swagger.enabled=true`（默认关闭）。
- **可配置项**：
  - `frame.me.swagger.enabled` — 是否启用，默认 `false`。
  - `frame.me.swagger.title` — 文档标题，默认 `Frame Me API`。
  - `frame.me.swagger.description` — 文档描述，默认 `Frame Me 接口文档`。
  - `frame.me.swagger.version` — 版本，默认 `1.0.0`。
  - `frame.me.swagger.contact.name/email/url` — 联系人信息。
  - `frame.me.swagger.groups` — API 分组列表；未配置时默认注册一个名为 `default`、匹配所有路径的分组。
- **设计约定**：
  - 不纳入 `frame-me-booter`，由业务 `xx-service` 按需引入。
  - 在 `frame-me-starter-base` 中通过 Maven profile `swagger` 引入：`mvn ... -Pswagger`。

**示例配置**：

```yaml
frame:
  me:
    swagger:
      enabled: true
      title: Frame Me Tester API
      description: Frame Me Tester 接口文档
      version: 1.0.0
      groups:
        - name: tester-api
          paths-to-match:
            - /api/**
```

访问地址：

- API Docs：`/v3/api-docs`
- Swagger UI：`/swagger-ui.html`

## `frame-me-starter-auth`

- **定位**：认证授权模块（当前为占位）。
- **依赖**：`frame-me-starter-base`、`lombok`。
- **关键类**：
  - `com.frame.me.auth.AuthConstant` — 占位常量接口。
- **扩展提示**：未来可引入 Spring Security、JWT、OAuth2、登录/权限相关逻辑。

## `frame-me-starter-cloud`

- **定位**：微服务云组件模块（当前为占位）。
- **依赖**：`frame-me-starter-base`、`lombok`。
- **关键类**：
  - `com.frame.me.cloud.CloudConstant` — 占位常量接口。
- **扩展提示**：未来可引入 Nacos 注册/配置中心、Gateway、Sentinel、分布式链路追踪等。

## `frame-me-starter-multi-redis`

- **定位**：Redis 基础能力 starter，封装 `spring-boot-starter-data-redis` 与统一操作工具 `RedisUtils`；在引入 Redisson 时自动启用 Redisson 高阶能力。
- **依赖**：`frame-me-starter-base`、`spring-boot-starter-data-redis`、`fastjson2`、`lombok`；`redisson` 为 optional 依赖。
- **关键类**：
  - `com.frame.me.redis.config.RedisAutoConfiguration` — Spring Data Redis 自动装配入口，创建 `StringRedisTemplate` / `RedisTemplate` 并初始化 `RedisUtils`。
  - `com.frame.me.redis.config.RedisProperties` — `frame.me.redis` 配置属性绑定（多实例、开关等）。
  - `com.frame.me.redis.config.RedissonLockAutoConfiguration` — Redisson 自动装配入口，创建 `RedissonClient` 并初始化所有 Redisson 工具类。
  - `com.frame.me.redis.config.RedissonProperties` — `spring.data.redis.redisson` 配置属性绑定。
  - `com.frame.me.redis.util.RedisUtils` — 统一 Redis 操作工具，支持 String、Hash、List、Set、ZSet、计数、简单分布式锁等。
  - `com.frame.me.redis.util.RedisClient` — 单实例 Redis 操作封装，供 `RedisUtils` 委托。
  - `com.frame.me.redis.util.RedissonLock` — Redisson 可重入锁静态入口（需引入 Redisson），支持看门狗续期。
  - `com.frame.me.redis.util.RedissonSync` — Redisson 同步原语：读写锁、公平锁、联锁、信号量、倒计时门闩、可过期信号量（红锁已随 Redisson 4.x 弃用）。
  - `com.frame.me.redis.util.RedissonTopic` — Redisson 消息能力：Topic、PatternTopic、ReliableTopic、Stream。
  - `com.frame.me.redis.util.RedissonLimiter` — Redisson 限流：基于 `RRateLimiter` 的令牌桶限流。
  - `com.frame.me.redis.RedisConstant` — 占位常量接口。
- **自动装配**：通过 `frame-me-starter-multi-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `RedisAutoConfiguration`、`RedissonLockAutoConfiguration`。
- **启用条件**：
  - `RedisAutoConfiguration`：类路径存在 `StringRedisTemplate`，`frame.me.redis.enabled=true`（默认开启，可显式关闭）。
  - `RedissonLockAutoConfiguration`：类路径存在 `org.redisson.api.RedissonClient`，`frame.me.redis.enabled=true`（默认开启）。
- **使用方式**：
  - 直接调用 `RedisUtils.xxx()` 使用 Spring Data Redis 能力。
  - 分布式锁默认为简单实现（`SET NX PX` + Lua 释放），不含看门狗续期；引入 Redisson 后自动启用 `RedissonLock`，提供可重入与看门狗续期。
  - Redisson 连接配置优先级：配 `spring.data.redis.redisson.config=classpath:redisson.yaml`（与 `redisson-spring-boot-starter` 标准配置项对齐）时用 Redisson 原生 YAML（支持全部 5 种模式，含 masterSlave/replicated）；否则自动复用 `spring.data.redis.*`——配 `cluster.nodes` 走集群、配 `sentinel.master/nodes` 走哨兵，否则单机，无需额外配置。
  - 用户名/密码在 Redisson 4.x 中需在顶层 `Config` 对象上设置，本 starter 已通过 `Config.setUsername` / `Config.setPassword` 实现。
- **设计约定**：
  - 已纳入 `frame-me-booter`，业务 `xx-service` 引入 `frame-me-booter` 即可获得 Redis 能力。
  - Redisson 为 optional 依赖，未引入时不影响 `RedisUtils` 使用。
  - 各 Redisson 工具类采用 `final` + 静态 `init(RedissonClient)` 模式，未初始化时调用会抛出 `IllegalStateException`。

**示例配置**：

```yaml
frame:
  me:
    redis:
      enabled: true
      # 额外实例（RedisUtils.getClient("name")），支持 standalone / cluster / sentinel
      clients:
        order:                    # 单机（默认）
          host: 10.0.0.1
          port: 6379
          password: pwd
        cache:                    # 集群（database 被忽略）
          mode: cluster
          nodes:
            - 10.0.0.2:6379
            - 10.0.0.3:6379
            - 10.0.0.4:6379
          password: pwd
        session:                  # 哨兵
          mode: sentinel
          sentinel-master: mymaster
          nodes:
            - 10.0.0.5:26379
            - 10.0.0.6:26379
          database: 1

spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
```

**示例代码**：

```java
RedisUtils.set("key", "value", Duration.ofMinutes(10));
String value = RedisUtils.get("key");
// 简单锁（默认，SET NX PX）
Boolean locked = RedisUtils.tryLock("lock:order:123", UUID.randomUUID().toString(), 30000);
RedisUtils.unlock("lock:order:123", UUID.randomUUID().toString());
// 可重入锁（引入 Redisson 后可用；leaseMs<=0 启用看门狗续期）
if (RedissonLock.tryLock("lock:order:123", 0, 30000)) {
    RedissonLock.unlock("lock:order:123");
}
// Redisson 读写锁
if (RedissonSync.tryWriteLock("lock:order", 0, 30000)) {
    RedissonSync.unlockWrite("lock:order");
}
// Redisson 限流（100 次/秒）
RedissonLimiter.trySetRate("api:order", RateType.OVERALL, 100, 1, TimeUnit.SECONDS);
boolean allowed = RedissonLimiter.tryAcquire("api:order", 1);
// Redisson Topic
RedissonTopic.topicPublish("order:event", new OrderEvent());
int listenerId = RedissonTopic.topicSubscribe("order:event", OrderEvent.class, (channel, msg) -> {
    // 处理消息
});
RedissonTopic.topicUnsubscribe("order:event", listenerId);
```

## `frame-me-starter-l1l2-cache`

- **定位**：两级缓存 starter，基于 JetCache 提供 Caffeine（L1）+ Redis（L2）缓存能力。
- **依赖**：`jetcache-starter-redis-lettuce`、`caffeine`、`lombok`。
- **关键类**：
  - `com.frame.me.cache.config.CacheAutoConfiguration` — 自动装配入口，启用方法级缓存注解。
  - `com.frame.me.cache.config.CacheProperties` — `frame.me.cache` 配置属性绑定。
  - `com.frame.me.cache.config.JetCacheInfrastructureRoleFixer` — 修复 JetCache 内部配置类的 BeanPostProcessor 警告。
  - `com.frame.me.cache.CacheConstant` — 占位常量接口。
- **自动装配**：通过 `frame-me-starter-l1l2-cache/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `CacheAutoConfiguration`。
- **启用条件**：
  - 类路径存在 JetCache 核心类。
  - `frame.me.cache.enabled=true`（默认关闭）。
- **使用方式**：
  - 在 Service 方法上标注 `@Cached(name = "...", cacheType = CacheType.BOTH)` 启用两级缓存。
  - 使用 `@CacheInvalidate` 在写操作时使缓存失效。
  - 使用 `@CacheUpdate` 在更新操作时更新缓存。
- **设计约定**：
  - 已纳入 `frame-me-booter`，业务 `xx-service` 引入 `frame-me-booter` 即可获得缓存能力。
  - 默认关闭，需显式开启。
  - 缓存连接参数通过 `jetcache.*` 原生属性控制。
  - 当前使用 `java` 序列化作为 value encoder，要求缓存对象实现 `Serializable`。

**示例配置**：

```yaml
frame:
  me:
    cache:
      enabled: true

jetcache:
  statIntervalMinutes: 15
  local:
    default:
      type: caffeine
      keyConvertor: fastjson2
      limit: 100
      expireAfterWriteInMillis: 600000
  remote:
    default:
      type: redis.lettuce
      keyConvertor: fastjson2
      valueEncoder: java
      valueDecoder: java
      poolConfig:
        minIdle: 5
        maxIdle: 20
        maxTotal: 50
      host: ${spring.data.redis.host:localhost}
      port: ${spring.data.redis.port:6379}
      password: ${spring.data.redis.password:}
      database: ${spring.data.redis.database:0}
      expireAfterWriteInMillis: 1800000
```

**示例注解**：

```java
@Cached(
    name = "demo:detail",
    key = "#id",
    cacheType = CacheType.BOTH,
    localLimit = 100,
    localExpire = 600,
    expire = 1800
)
public DemoVO getById(Long id) { ... }

@CacheInvalidate(name = "demo:detail", key = "#id")
public Boolean delete(Long id) { ... }
```

## `frame-me-starter-sensi-encrypt`

- **定位**：配置文件密钥加密 starter，基于 Jasypt 核心库在应用启动早期解密配置中的 `ME(密文)`，使数据源、Redis 等下游拿到明文。规避了官方 `jasypt-spring-boot` starter 在 Spring Boot 4 上的不兼容问题。
- **依赖**：`org.jasypt:jasypt`（纯加密库）、`spring-boot`（provided）、`lombok`。**不依赖 `frame-me-starter-base`**，保持轻量。
- **关键类**：
  - `com.frame.me.encrypt.env.EncryptablePropertyEnvironmentPostProcessor` — 实现 Boot 4 的 `org.springframework.boot.EnvironmentPostProcessor`，扫描属性源解密 `ME(...)`。
  - `com.frame.me.encrypt.util.JasyptEncryptor` — 统一构建 `StandardPBEStringEncryptor`（默认 `PBEWITHHMACSHA512ANDAES_256` + 随机盐 + 随机 IV）。
  - `com.frame.me.encrypt.cli.JasyptEncryptCli` — 离线生成 `ME(密文)` 的 `main` 工具。
  - `com.frame.me.encrypt.config.EncryptAutoConfiguration` — 配了主密码后暴露 `org.jasypt.encryption.StringEncryptor` Bean，供业务代码对自身数据加解密（与配置解密共用主密码与算法，密文互通）。
  - `com.frame.me.encrypt.EncryptConstant` — 配置键与默认值常量。
- **注册**：配置解密的 `EncryptablePropertyEnvironmentPostProcessor` 走 `META-INF/spring.factories` 的 `org.springframework.boot.EnvironmentPostProcessor` 键（EnvironmentPostProcessor 早于自动装配，**不能**用 `AutoConfiguration.imports`）；业务用的 `EncryptAutoConfiguration` 是普通自动装配，走 `AutoConfiguration.imports`。两者并存、互不影响。
- **启用条件**：读到主密码 `frame.me.encrypt.password`（兼容环境变量 `ME_ENCRYPT_PASSWORD`、JVM 系统属性）时才解密；主密码缺失则跳过，对无密文应用零影响。
- **可配置项**（前缀 `frame.me.encrypt`）：`password`、`algorithm`（默认 `PBEWITHHMACSHA512ANDAES_256`）、`iterations`（默认 1000）、`prefix`/`suffix`（默认 `ME(` / `)`）。
- **使用方式**：
  - 生成密文：`java -cp ... com.frame.me.encrypt.cli.JasyptEncryptCli <明文> <主密码>`。
  - 配置：把敏感值写成 `password: ME(密文)`。
  - 运行：通过环境变量/启动参数注入主密码，**不写入配置文件**：`ME_ENCRYPT_PASSWORD=xxx` 或 `-Dframe.me.encrypt.password=xxx`。
- **设计约定**：
  - 已纳入 `frame-me-booter`，业务 `xx-service` 引入 `frame-me-booter` 即获得能力。
  - 跳过系统环境变量属性源（规避 Boot 3.5+ 系统环境源不被包装解密的已知行为，且密文放环境变量无意义）。
  - 本质是「用主密码加密其它密钥」，主密码仍需妥善保管；若要求密钥完全不落地，应改用 Vault/KMS 方案。

## `frame-me-booter`

- **定位**：聚合启动模块 / service 入口，本身不包含业务代码，用于把一组通用 starter 打包成一条依赖对外提供。
- **依赖**：`frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-dynamic-ds`、`frame-me-starter-multi-redis`、`frame-me-starter-l1l2-cache`、`frame-me-starter-sensi-encrypt`（通过传递依赖自动引入 `frame-me-starter-base` 与 `frame-me-api`）。
- **关键类**：
  - `com.frame.me.booter.BooterConstant` — 占位常量接口。
- **使用方**：业务工程的 `xx-service` 模块。
- **设计约定**：
  - 业务 `xx-service` 通过引入 `frame-me-booter` 一键启动通用能力。
  - `frame-me-booter` 无自己的自动装配类，依赖的 `frame-me-starter-base` 等模块会通过传递依赖自动注册。
  - `frame-me-adapter`（含 `frame-me-adapter-starter`）与 `frame-me-starter-doc-openapi` 不纳入聚合，因为不同项目通常会重写适配层或按需引入文档能力。

```xml
<!-- 业务 xx-service：引入通用能力 -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-booter</artifactId>
</dependency>

<!-- 默认适配层 / 老接口规范（可选，可被项目自定义适配层替换） -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-adapter-starter</artifactId>
</dependency>

<!-- 接口文档（可选） -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-starter-doc-openapi</artifactId>
</dependency>
```

- **扩展提示**：新增通用功能子模块后，应将其加入 `frame-me-booter` 的依赖列表；`frame-me-adapter-starter`、`frame-me-starter-doc-openapi` 等可替换/可选模块应保持独立，不加入 `frame-me-booter`。

## `frame-me-tester`

- **定位**：示例与验证聚合模块，本身不包含源码，仅聚合 `frame-me-tester-api` 与 `frame-me-tester-service`。
- **子模块**：
  - `frame-me-tester-api`：契约接口层。
  - `frame-me-tester-service`：实现层与可运行入口。

## `frame-me-tester-api`

- **定位**：示例业务契约模块，演示业务 `xx-api` 应如何组织。
- **依赖**：`frame-me-api`。
- **关键类**：
  - `com.frame.me.tester.api.IDemoApi` — 演示数据 API 契约，使用 Spring HTTP Interface（`@HttpExchange`、`@GetExchange`、`@PostExchange` 等）。
  - `com.frame.me.tester.api.IHealthApi` — 健康检查 API 契约。
  - `com.frame.me.tester.api.IDataSourceApi` — 数据源切换与连接池信息查询契约。
  - `com.frame.me.tester.api.dto.DemoDTO` — 演示数据传输对象，含校验分组。
  - `com.frame.me.tester.api.query.DemoQuery` — 演示分页查询参数。
  - `com.frame.me.tester.api.query.DemoComplexQuery` — 演示复杂查询参数（含 `@TimeRange`）。
  - `com.frame.me.tester.api.vo.DemoVO` — 演示返回视图对象。
  - `com.frame.me.tester.api.vo.DemoComplexVO` — 演示复杂查询返回视图对象。
- **设计约定**：
  - API 契约推荐用 Spring HTTP Interface 声明，便于后续生成 HTTP 客户端。
  - DTO / Query / VO 放在 `xx-api` 中，供服务提供方与消费方共享。

## `frame-me-tester-service`

- **定位**：示例业务实现层与可运行 Spring Boot 入口。
- **依赖**：`frame-me-tester-api`、`frame-me-booter`、`frame-me-adapter-starter`、`spring-boot-starter-test`（test scope）。
- **关键类/文件**：
  - `com.frame.me.tester.Application` — `@SpringBootApplication` 启动类。
  - `com.frame.me.tester.controller.HealthController` — 实现 `IHealthApi`，故意触发 NPE 以验证异常处理。
  - `com.frame.me.tester.controller.DemoController` — 实现 `IDemoApi`，演示 MyBatis-Plus CRUD、分页、校验分组。
  - `com.frame.me.tester.controller.DataSourceController` — 实现 `IDataSourceApi`，演示多数据源切换与连接池信息查询。
  - `com.frame.me.tester.service.IDemoService` / `com.frame.me.tester.service.impl.DemoServiceImpl` — 演示 Service 层。
  - `com.frame.me.tester.service.convert.DemoConvert` — MapStruct 转换器（`@Mapper(componentModel = "spring")`）。
  - `com.frame.me.tester.entity.DemoEntity` — 演示实体，继承 `BaseVersionEntity`，对应表 `demo_user`。
  - `com.frame.me.tester.mapper.DemoMapper` — 演示 Mapper，继承 MyBatis-Plus `BaseMapper<DemoEntity>`。
  - `com.frame.me.tester.ApplicationTests` — 上下文加载测试。
  - `com.frame.me.tester.AbstractIntegrationTest` — Testcontainers + MySQL 集成测试基类。
  - `frame-me-tester/frame-me-tester-service/src/main/resources/application.yml` — 端口 `8080`，应用名 `frame-me-tester`，单/多数据源、MyBatis-Plus、OpenAPI、P6Spy 配置。
- **构建插件**：包含 `spring-boot-maven-plugin`，用于打包可运行 Jar。
- **扩展提示**：作为集成验证入口，新模块加入后应在此添加对应的集成测试或示例 Controller。

## 模块依赖速查表

| 模块 | 直接依赖 |
|---|---|
| `frame-me-api` | 无内部依赖 |
| `frame-me-starter-base` | `frame-me-api` |
| `frame-me-adapter-api` | `frame-me-api` |
| `frame-me-adapter-starter` | `frame-me-adapter-api`、`frame-me-starter-base` |
| `frame-me-starter-dynamic-ds` | `frame-me-starter-base` |
| `frame-me-starter-doc-openapi` | 无内部框架依赖（仅 `springdoc-openapi-starter-webmvc-ui`） |
| `frame-me-starter-auth` | `frame-me-starter-base` |
| `frame-me-starter-cloud` | `frame-me-starter-base` |
| `frame-me-booter` | `frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-dynamic-ds` |
| `frame-me-tester-api` | `frame-me-api` |
| `frame-me-tester-service` | `frame-me-tester-api`、`frame-me-booter`、`frame-me-adapter-starter` |
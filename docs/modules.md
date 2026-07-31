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
  - `com.frame.me.api.annotation.QueryMap` — HTTP Interface 查询参数映射注解。
  - `com.frame.me.api.enums.IEnum` — 通用枚举接口。
  - `com.frame.me.api.enums.GenderEnum` — 示例枚举（实现 `IEnum`）。
  - `com.frame.me.api.enums.YesNoEnum` — 是/否枚举（实现 `IEnum`）。
  - `com.frame.me.event.MeApplicationEvent` — 可桥接的本地事件基类。
  - `com.frame.me.event.IEventType<T>` — 事件类型映射接口。
  - `com.frame.me.event.EventBridgeMessage` — 跨服务传输的通用包装。
  - `com.frame.me.event.EventClientPermit` — 允许通过 SSE/WebSocket 推送给客户端的事件标记注解。
- **使用方**：业务工程的 `xx-api` 模块。
- **设计约定**：
  - 业务 `xx-api` 通过引入 `frame-me-api` 获得统一的接口契约与校验分组。
  - 业务 `xx-api` 之间可以相互引用，用于跨业务接口调用。
  - `frame-me-api` 不实现任何具体能力，只定义最基础的跨模块接口、常量、分页模型与校验契约。

## `frame-me-starter-base`

- **定位**：Spring Web 基础设施模块，提供统一响应、异常处理、全局异常处理、`IResult<T>` 实现、Filter 层错误响应 SPI、事件桥接、HTTP Interface 客户端、池化 `RestClient`、异步/调度线程池、CORS 跨域处理等能力。
- **依赖**：`frame-me-api`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-actuator`、`spring-boot-starter-restclient`、`hutool-all`、`fastjson2`、`lombok`。
- **关键类**：
  - `com.frame.me.base.advice.GlobalExceptionHandler` — 全局异常处理。
  - `com.frame.me.base.config.BaseAutoConfiguration` — 自动装配入口。
  - `com.frame.me.base.config.ExceptionProperties` — `me.exception.*` 配置属性绑定。
  - `com.frame.me.base.env.EnvironmentHelper` — 获取 Spring active profile、判断当前环境（dev/test/prod/daily/pre）。
    - 提供 `getActiveProfiles()`、`getActiveProfile()`、`isProfileActive(String)`、`isDev()`、`isTest()`、`isProd()`、`isDaily()`、`isPre()` 等方法。
  - `com.frame.me.base.result.ResultCode` — 状态码枚举。
  - `com.frame.me.base.result.Result<T>` — `IResult<T>` 默认实现，并提供静态工厂方法。
  - `com.frame.me.base.exception.BusinessException` — 业务异常。
  - `com.frame.me.base.exception.InternalException` — 内部异常。
  - `com.frame.me.base.exception.RetryException` — 重试异常。
  - `com.frame.me.base.result.ResultJacksonModule` — 将 `IResult` 抽象类型反序列化映射为 `Result` 的 Jackson 模块。
  - `com.frame.me.base.client.HttpServiceClientAutoConfiguration` — HTTP Interface 客户端自动装配（注册 `HttpServiceProxyFactory`）。
  - `com.frame.me.base.client.QueryObjectArgumentResolver` — 将 `@QueryMap` 注解的查询对象解析为查询参数；`Collection` 与数组属性（含基本类型数组）逐项展开为重复参数。
  - `com.frame.me.base.event.EventBridgePublisher` — 事件桥接发布入口：本地发布 + 选择 transport 广播。
  - `com.frame.me.base.event.EventBridgeListener` — 订阅通道、按 `type` 分发、还原为本地事件。
  - `com.frame.me.base.event.IEventTransport` — 传输通道抽象（`send` / `subscribe`）。
  - `com.frame.me.base.event.EventBridgeProperties` — `me.event-bridge.*` 配置属性绑定。
  - `com.frame.me.base.event.EventBridgeAutoConfiguration` — 事件桥接自动装配入口。
  - `com.frame.me.base.notify.INotifySender` — 通用通知发送接口，业务代码通过它发送通知而无需关心底层通道。
  - `com.frame.me.base.config.AsyncAutoConfiguration` / `com.frame.me.base.config.AsyncProperties` — 默认 `@Async` 线程池与未捕获异常处理；异常通知发送失败仅降级为 warn，不会逃逸出异常处理器。
  - `com.frame.me.base.config.SchedulingAutoConfiguration` / `com.frame.me.base.config.SchedulingProperties` — 默认 `@Scheduled` 调度线程池；调度异常处理同上，通知故障不影响后续调度。
  - `com.frame.me.base.config.PoolingRestClientAutoConfiguration` / `com.frame.me.base.config.PoolingRestClientProperties` — 基于 HttpClient 5 的池化 `RestClient.Builder` 自动配置。
  - `com.frame.me.base.user.User` — 通用用户模型占位类；`password` 字段标记 `@ToString.Exclude`，口令哈希不随日志打印落盘。
  - `com.frame.me.base.util.SnowflakeUtils` — 雪花 ID 生成工具，优先使用 MyBatis-Plus / MyBatis-Flex 的生成器实例，其次使用 base 的 `Snowflake` Bean，最后回退到 Hutool 默认生成器。
  - `com.frame.me.base.web.IFilterErrorResponseWriter` — Filter 层错误响应写入器 SPI，允许业务模块自定义 Filter 层错误消息体格式。
  - `com.frame.me.base.web.ResultFilterErrorResponseWriter` — 默认实现，输出 `Result` 格式 JSON。
  - `com.frame.me.base.config.CorsAutoConfiguration` / `com.frame.me.base.config.CorsProperties` — CORS 跨域自动配置（默认关闭，`me.cors.enabled=true` 开启）；带 `@ConditionalOnWebApplication(type=SERVLET)`，非 Web 应用（纯消息/定时任务服务）不装配。开启后注册最高优先级（`HIGHEST_PRECEDENCE`）的 `CorsFilter`，早于 `AuthFilter` 处理 OPTIONS 预检并附加 CORS 响应头。认证链（`AuthFilter` / `PermissionFilter` / `PermissionInterceptor` / sa-token `SaInterceptor`）对 OPTIONS 预检亦各自豁免作兜底，避免预检被鉴权拦截返回 401/403 导致浏览器跨域失败；豁免口径统一为 **OPTIONS 且带 `Origin` 头**——无 `Origin` 的 OPTIONS 非真预检，仍走正常鉴权链以防绕过。
- **自动装配**：通过 `frame-me-starter-base/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `BaseAutoConfiguration`、`CorsAutoConfiguration`、`HttpServiceClientAutoConfiguration`、`PoolingRestClientAutoConfiguration`、`AsyncAutoConfiguration`、`SchedulingAutoConfiguration`、`EventBridgeAutoConfiguration`。
- **可配置项**：
  - `me.async.enabled` — 是否启用默认 `@Async` 线程池，默认 `true`。
  - `me.async.core-pool-size` — 核心线程数，默认 `4`。
  - `me.async.max-pool-size` — 最大线程数，默认 `16`。
  - `me.async.queue-capacity` — 任务队列容量，默认 `256`。
  - `me.async.keep-alive-seconds` — 非核心线程空闲存活时间（秒），默认 `60`。
  - `me.async.thread-name-prefix` — 线程名前缀，默认 `frame-me-async-`。
  - `me.async.allow-core-thread-time-out` — 是否允许核心线程超时回收，默认 `false`。
  - `me.async.await-termination-seconds` — 应用关闭时等待任务完成的最大秒数，`0` 表示不等待，默认 `0`。
  - `me.async.rejection-policy` — 拒绝策略，可选 `ABORT`、`CALLER_RUNS`、`DISCARD`、`DISCARD_OLDEST`，默认 `CALLER_RUNS`。
  - `me.async.exception-handler-enabled` — 是否注册默认异步异常处理器，默认 `true`。
  - `me.async.exception-notify-enabled` — 异步方法异常时是否尝试发送通知，默认 `true`。
  - `me.async.exception-notify-receivers` — 异步异常通知接收者列表，默认空列表；为空时由 `INotifySender` 实现回退到 `me.notify.global-receivers`。
  - `me.async.exception-include-stacktrace` — 异步异常通知内容是否包含完整堆栈，默认 `false`（仅发送异常类名与 message，避免堆栈信息外泄）。
  - `me.scheduling.pool-size` — 调度线程池大小，默认 `4`。
  - `me.scheduling.thread-name-prefix` — 调度线程名前缀，默认 `me-scheduling-`。
  - `me.scheduling.remove-on-cancel-policy` — 取消任务后是否立即从线程池移除，默认 `false`。
  - `me.scheduling.await-termination-seconds` — 应用关闭时等待任务完成的最大秒数，`0` 表示不等待，默认 `0`。
  - `me.scheduling.exception-handler-enabled` — 是否注册默认调度异常处理器，默认 `true`。
  - `me.scheduling.exception-notify-enabled` — 调度任务异常时是否尝试发送通知，默认 `true`。
  - `me.scheduling.exception-notify-receivers` — 调度异常通知接收者列表，默认空列表；为空时由 `INotifySender` 实现回退到 `me.notify.global-receivers`。
  - `me.scheduling.exception-include-stacktrace` — 调度异常通知内容是否包含完整堆栈，默认 `false`（仅发送异常类名与 message，避免堆栈信息外泄）。
  - `me.exception.include-stacktrace` — 全局异常响应（`Result.err`）是否包含完整堆栈，默认 `false`（fail-closed）；排查问题时显式设为 `true`。
  - `me.exception.mask-unknown-message` — 兜底未知异常对外是否屏蔽真实 message，默认 `false`（返回异常自身 message，兼容原行为）；设为 `true` 时对外固定返回通用文案（"系统错误"），真实 message 只进服务端日志，对外服务建议开启。
  - `me.restclient.pool.max-per-route` — 每个路由的最大连接数，默认 `50`。
  - `me.event-bridge.enabled` — 是否启用事件桥接，默认 `true`。
  - `me.event-bridge.service-name` — 当前服务名，默认取 `spring.application.name`；两者都未配置时生成 `unknown-<uuid>` 实例唯一名（warn 提示）。用于事件来源追踪与自身消息过滤，自过滤依赖非 `unknown` 的服务名。
  - `me.event-bridge.topic-prefix` — Redis Topic 前缀，默认 `me:event:`。
  - `me.event-bridge.default-transport` — 默认传输通道名称，默认 `redis`。
  - `me.event-bridge.transports` — 按事件类型指定传输通道，key 为事件类型，value 为 transport Bean 名称。
  - `me.cors.enabled` — 是否启用 CORS 跨域处理，默认 `false`（CORS 是业务相关能力，需跨域的服务显式开启）。开启后注册最高优先级 `CorsFilter`，在认证过滤器之前处理 OPTIONS 预检并附加 CORS 响应头。
  - `me.cors.allowed-origins` — 允许的来源列表，为空时默认放行所有来源（`*`）；显式配置后仅允许列出的来源。底层用 `addAllowedOriginPattern` 注册，pattern 模式下即使 `allowCredentials=true` 也可用 `*`，框架自动处理回显合规；需严格白名单则显式列出。
  - `me.cors.allowed-methods` — 允许的 HTTP 方法，默认 `GET,POST,PUT,DELETE,PATCH,OPTIONS,HEAD`。
  - `me.cors.allowed-headers` — 允许的请求头，默认 `Authorization,Content-Type,Accept,X-Requested-With`。
  - `me.cors.exposed-headers` — 暴露给浏览器可读的响应头，默认 `Cache-Control,Content-Disposition`。
  - `me.cors.allow-credentials` — 是否允许携带凭据（Cookie），默认 `false`。
  - `me.cors.max-age` — 预检结果缓存时长（秒），默认 `3600`。
- **扩展提示**：与 Spring Web 相关的基础能力（拦截器、参数解析器、统一日志等）适合放在这里。
- **日志模板**：`src/main/resources/logback-frame-me.xml` 是共享日志模板（appender、按 profile 分级），**刻意不用 `logback-spring.xml` 命名**，避免二方库劫持应用日志配置；应用在自己的 `logback-spring.xml` 中通过 `<include resource="logback-frame-me.xml"/>` 显式引入（`frame-me-tester-service` 即此用法），业务专属 logger 在应用侧追加。

`frame-me-tester/frame-me-tester-service` 提供两个 Maven Profile 用于演示：

- `p6spy` — 引入 `p6spy-spring-boot-starter`，用于 SQL 监控：`mvn ... -Pp6spy`。
- `swagger` — 引入 `frame-me-starter-doc-openapi`，用于接口文档：`mvn ... -Pswagger`。

**`@Async` 使用示例**：

```java
@Service
public class DemoService {

    @Async
    public void runAsync() {
        // 当前线程名以 frame-me-async- 开头
    }
}
```

业务工程无需再添加 `@EnableAsync` 或声明 `ThreadPoolTaskExecutor`；若需自定义，声明同名 `taskExecutor` Bean 或自定义 `AsyncConfigurer` 即可覆盖。关闭默认配置：

```yaml
me:
  async:
    enabled: false
```

> **自定义线程池注意**：业务自行声明 `ThreadPoolTaskExecutor` 时框架默认池退避，`AuthContextTaskDecorator`（认证上下文）/`AuthPermissionTaskDecorator`（权限上下文）不会自动挂到自定义池上，需业务自行通过 `setTaskDecorator` 挂载，否则 `@Async` 方法内的认证/权限上下文传播静默失效。`me.async.enabled=false` 退到 Spring Boot 默认池则无此问题（Boot 4 会自动组合容器中所有 `TaskDecorator`）。

**`@Scheduled` 使用示例**：

```java
@Service
public class DemoSchedulingService {

    @Scheduled(fixedRate = 60_000)
    public void heartbeat() {
        // 当前线程名以 frame-me-scheduling- 开头
    }
}
```

业务工程无需再添加 `@EnableScheduling` 或声明 `TaskScheduler`；若需自定义，声明同名 `taskScheduler` Bean 即可覆盖。关闭默认配置：

```yaml
me:
  scheduling:
    enabled: false
```

## `frame-me-starter-mybatis-plus`

- **定位**：MyBatis-Plus 数据访问 starter，从 `frame-me-starter-base` 抽取独立，提供实体基类、分页插件、乐观锁、公共字段自动填充与雪花 ID 生成能力。
- **依赖**：`frame-me-api`、`mybatis-plus-spring-boot4-starter`、`mybatis-plus-jsqlparser`、`spring-boot-starter-jdbc`、`mysql-connector-j`、`lombok`。
- **关键类**：
  - `com.frame.me.mybatis.plus.entity.BaseEntity` — 基础实体，含 `id`（雪花算法）、`createTime`、`updateTime`、`deleted`。
  - `com.frame.me.mybatis.plus.entity.BaseVersionEntity` — 继承 `BaseEntity`，额外提供 `version`（乐观锁）。
  - `com.frame.me.mybatis.plus.plugin.BaseMetaObjectHandler` — 公共字段自动填充，需通过 `me.mybatis.meta-object-handler.enabled=true` 开启。
  - `com.frame.me.mybatis.plus.util.PageUtils` — 新规范分页工具，`PageQuery` / `PageData` 与 MyBatis-Plus `Page` 转换。
  - `com.frame.me.mybatis.plus.config.MybatisPlusProperties` — `me.mybatis` 配置属性绑定。
  - `com.frame.me.mybatis.plus.config.MybatisPlusConfiguration` — 分页插件、乐观锁插件、公共字段自动填充处理器以及可选的自定义 ID 生成器注册。
- **自动装配**：通过 `frame-me-starter-mybatis-plus/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `MybatisPlusConfiguration`。
- **可配置项**：
  - `me.mybatis.meta-object-handler.enabled` — 是否启用公共字段自动填充，默认 `false`。
  - `me.mybatis.snowflake.worker-id` — 雪花算法 workerId，范围 `0~31`；未配置时使用 MyBatis-Plus 默认推导值。
  - `me.mybatis.snowflake.datacenter-id` — 雪花算法 datacenterId，范围 `0~31`，默认 `0`；未配置时使用 MyBatis-Plus 默认推导值。
  - `me.snowflake.worker-id` — 基础雪花算法 workerId，未配置时回退到 Hutool 默认生成器（定义在 `frame-me-starter-base`）。
  - `me.snowflake.datacenter-id` — 基础雪花算法 datacenterId，默认 `0`（定义在 `frame-me-starter-base`）。
- **设计约定**：
  - 表名到实体名映射：去掉第一个下划线前缀，例如 `spo_fms_device` → `FmsDevice`。
  - **Mapper 接口必须标注 `@Mapper` 注解**，并继承 MyBatis-Plus `BaseMapper<T>`，以便自动扫描与通用 CRUD。
  - **不纳入 `frame-me-boot`**，业务 `xx-service` 需显式引入 `frame-me-starter-mybatis-plus` 以获得 MyBatis-Plus 数据访问能力。

## `frame-me-starter-mybatis-flex`

- **定位**：MyBatis-Flex 数据访问 starter，作为 MyBatis-Plus 的替代方案，提供实体基类、分页工具与雪花 ID 适配能力。
- **依赖**：`frame-me-api`、`frame-me-starter-base`、`mybatis-flex-spring-boot4-starter`、`spring-boot-starter-jdbc`、`mysql-connector-j`、`lombok`。
- **关键类**：
  - `com.frame.me.mybatis.flex.entity.BaseEntity` — 基础实体，含 `id`（雪花算法）、`createTime`、`updateTime`、`deleted`。
  - `com.frame.me.mybatis.flex.entity.BaseVersionEntity` — 继承 `BaseEntity`，额外提供 `version`（乐观锁）。
  - `com.frame.me.mybatis.flex.util.PageUtils` — 分页工具，`PageQuery` / `PageData` 与 MyBatis-Flex `Page` 转换。
  - `com.frame.me.mybatis.flex.config.MybatisFlexConfiguration` — 自动装配入口，注册全局配置；当 base 的 `SnowflakeUtils` 可用且配置了 `me.snowflake.worker-id` 时，自动将 flex 内置雪花生成器委托给 `SnowflakeUtils`。
  - `com.frame.me.mybatis.flex.config.MybatisFlexInfrastructureRoleFixer` — 修复 MyBatis-Flex 内部配置类在 BeanPostProcessor 阶段被提前实例化而产生的 WARN。
- **自动装配**：通过 `frame-me-starter-mybatis-flex/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `MybatisFlexConfiguration`。
- **启用条件**：类路径存在 `com.mybatisflex.core.BaseMapper`。
- **设计约定**：
  - 与 `frame-me-starter-mybatis-plus` **二选一**，不可同时引入。
  - **不纳入 `frame-me-boot`**，业务 `xx-service` 需显式引入以替换默认的 MyBatis-Plus。
  - 雪花 ID 自动复用 base 的 `SnowflakeUtils`，与 MyBatis-Plus 使用同一套雪花实例。

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
- **依赖**：`frame-me-adapter-api`、`frame-me-starter-base`、`lombok`；`frame-me-starter-mybatis-plus` 为 **optional**，仅在需要使用 `PageableUtils` 时由消费方显式引入。
- **关键类**：
  - `com.frame.me.adapter.advice.Result2ResponseAdvice` — `ResponseBodyAdvice`，将 `IResult<T>` 转为 `Response<T>`。
  - `com.frame.me.adapter.result.Response<T>` — 外部响应结构。
  - `com.frame.me.adapter.result.ResponseJacksonModule` — 将 `IResult` 抽象类型映射为 `Response` 的 Jackson 模块。
  - `com.frame.me.adapter.mybatis.util.PageableUtils` — 老规范分页工具，`PageParam` / `PageResult` 与 MyBatis-Plus `Page` 转换；排序列名经 `SAFE_COLUMN` 白名单校验防 ORDER BY 注入，`pageSize` 超 500 强制截断；**需要消费方显式引入 `frame-me-starter-mybatis-plus` 才可用**。
  - `com.frame.me.adapter.web.ResponseFilterErrorResponseWriter` — 覆盖 `IFilterErrorResponseWriter`，使 Filter 层错误响应输出 `Response` 格式。
  - `com.frame.me.adapter.config.AdapterAutoConfiguration` — 自动装配入口。
  - `com.frame.me.adapter.AdapterConstant` — 占位常量类。
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
  - `me.dynamic-datasource.enabled=true`（默认 `true`，可省略）。
  - `spring.datasource.dynamic.enabled=true`（默认 `true`，可省略）。
- **使用方式**：
  - 当存在 `spring.datasource.url` 时，自动创建 `master` 数据源。
  - 若 `spring.datasource.dynamic.datasource` 中也显式配置了 `master`，则显式配置优先级更高，会覆盖自动创建的 `master`。
  - 支持读取 `spring.datasource.hikari.*` 和 `spring.datasource.druid.*` 连接池属性；属性按 Spring 优先级解析（高优先级源先占位、低优先级源不覆盖，与 `environment.getProperty` 一致）。
  - `me.mybatis` 互斥：`frame-me-starter-mybatis-plus` 与 `frame-me-starter-mybatis-flex` 不可同时引入（`BaseMapper`/实体基类/基础设施冲突，同时引入会启动失败）；二者各自 `@ConditionalOnClass` 检测，业务按需选择其一。
  - 需要切换数据源时，使用 `@DS("slave")` 等 baomidou 注解。
- **设计约定**：
  - **不纳入 `frame-me-boot`**，业务 `xx-service` 需显式引入 `frame-me-starter-dynamic-ds` 以获得多数据源能力。

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
- **依赖**：`spring-boot-autoconfigure`、`springdoc-openapi-starter-webmvc-ui`、`lombok`。
- **关键类**：
  - `com.frame.me.doc.openapi.config.DocOpenApiAutoConfiguration` — 自动装配入口。
  - `com.frame.me.doc.openapi.config.DocOpenApiProperties` — `me.swagger` 配置属性绑定。
  - `com.frame.me.doc.openapi.config.GroupedOpenApiRegistrar` — 动态注册 API 分组。
  - `com.frame.me.doc.openapi.DocOpenApiConstant` — 占位常量类。
- **自动装配**：通过 `frame-me-starter-doc-openapi/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `DocOpenApiAutoConfiguration`。
- **启用条件**：
  - 类路径存在 `io.swagger.v3.oas.models.OpenAPI`。
  - `me.swagger.enabled=true`（默认 `true`，可通过 `me.swagger.enabled=false` 显式关闭）。
- **可配置项**：
  - `me.swagger.enabled` — 是否启用，默认 `true`。
  - `me.swagger.title` — 文档标题，默认 `Frame Me API`。
  - `me.swagger.description` — 文档描述，默认 `Frame Me 接口文档`。
  - `me.swagger.version` — 版本，默认 `1.0.0`。
  - `me.swagger.contact.name/email/url` — 联系人信息。
  - `me.swagger.groups` — API 分组列表；未配置时默认注册一个名为 `default`、匹配所有路径的分组。同名分组启动时会 warn 提示（SpringDoc 运行时仅保留最后一个，bean 名后缀仅避免启动期冲突）。
- **设计约定**：
  - 不纳入 `frame-me-boot`，由业务 `xx-service` 按需引入。
  - 在 `frame-me-starter-base` 中通过 Maven profile `swagger` 引入：`mvn ... -Pswagger`。
  - **生产安全**：文档端点默认开启（`matchIfMissing=true`），生产环境务必设 `me.swagger.enabled=false` 关闭，避免接口结构外泄。
  - **Jackson 2 并存（已知项）**：`springdoc-openapi 3.0.3` 依赖 `swagger-core-jakarta 2.2.x`，后者仍硬编码 Jackson 2（`com.fasterxml.jackson`），与 Boot 4 的 Jackson 3（`tools.jackson`）并存。仅 `-Pswagger` profile 激活时才进入业务 classpath，默认构建不带；Jackson 2/3 包名不同运行期不冲突，swagger-core 用 Jackson 2 解析 OpenAPI spec 自身 yaml/json，与业务 Jackson 3 `ObjectMapper` 隔离。上游 swagger-core/SpringDoc 尚未适配 Jackson 3，无法通过改 pom 消除，待上游升级后跟进。

**示例配置**：

```yaml
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

- **定位**：认证抽象层，不绑定具体认证框架。提供统一的用户上下文、注解、SPI 和扩展点；具体认证实现由独立 starter 接管，当前已有 `frame-me-starter-auth-jwt` 与 `frame-me-starter-auth-sa-token` 两种可选实现（`frame-me-starter-auth-security` 等仍可后续扩展）。另带一个基于请求头的极简兜底实现（默认关闭，仅内网服务间调用显式开启）。RBAC 授权能力已独立到 `frame-me-starter-auth-rbac`。
- **依赖**：`frame-me-starter-base`、`lombok`；`frame-me-starter-op-audit` 为 optional 依赖，用于提供审计操作人 SPI 实现；`spring-security-crypto` 为 optional 依赖（`PasswordUtils` 的 BCrypt 实现），由真正做账号密码认证的实现 starter 显式声明；`spring-cloud-commons` 为 optional 依赖，存在 Spring Cloud 注册中心时用于甄别服务名调用以决定认证头传播。
- **关键类**：
  - `com.frame.me.auth.config.AuthAutoConfiguration` — 自动装配入口。
  - `com.frame.me.auth.config.AuthProperties` — `me.auth.*` 配置属性绑定。
  - `com.frame.me.auth.core.AuthContext` — ThreadLocal 当前用户上下文。
  - `com.frame.me.auth.core.AuthUserAuthenticator` — 账号密码认证器（查用户 → 401 → 校验密码 → 401），供 JWT / Sa-Token 等认证实现的 `login` 共用。
  - `com.frame.me.auth.core.HeaderAuthUserResolver` — 基于请求头（`X-User-Id`）的兜底用户解析器，默认不装配，需 `me.auth.header-resolver.enabled=true` 显式开启。
  - `com.frame.me.auth.spi.IAuthService` — 登录/登出/按用户 ID 强制登出/刷新/校验认证服务接口。
  - `com.frame.me.auth.spi.IAuthUserResolver` — 请求解析当前用户接口。
  - `com.frame.me.auth.spi.IAuthUserDetailsService` — 用户详情服务 SPI（按账号/ID 查询用户、校验密码），供 JWT / Sa-Token 等认证实现共用；`matches` 为 default 方法（BCrypt），业务换算法时覆盖。
  - `com.frame.me.auth.web.dto.LoginDTO` / `com.frame.me.auth.web.vo.TokenVO` — 登录请求与 Token 响应，JWT / Sa-Token 实现共用。
  - `com.frame.me.auth.util.PasswordUtils` — BCrypt 密码加解密工具。
  - `com.frame.me.auth.annotation.LoginUser` — 注入当前用户参数注解。
  - `com.frame.me.auth.annotation.Anonymous` — 匿名访问白名单注解。
  - `com.frame.me.auth.filter.AuthFilter` — 认证过滤器，解析并写入当前用户；ERROR dispatch（容器 `/error` 转发）直接放行，不掩盖 404/servlet 级异常的真实状态码；OPTIONS 预检请求带 `Origin` 头时直接放行，不参与认证（预检由 `CorsFilter` 在更早优先级处理，此处为兜底；无 `Origin` 的 OPTIONS 非真预检，走正常鉴权链防绕过）。
  - `com.frame.me.auth.resolver.LoginUserArgumentResolver` — `@LoginUser` 参数解析器。
  - `com.frame.me.auth.audit.AuditAuthOperatorSupplier` — 审计操作人提供者实现。
- **自动装配**：通过 `frame-me-starter-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `AuthAutoConfiguration`。
- **可配置项**：
  - `me.auth.enabled` — 是否启用认证模块，默认 `true`。
  - `me.auth.whitelist` — 匿名访问白名单路径列表（Ant 风格通配符），默认空；命中白名单的路径跳过认证校验。按**应用内路径**匹配（不含 `server.servlet.context-path`）；配置误带 context-path 前缀时自动剥离前缀平滑兼容。
  - `me.auth.header-resolver.enabled` — 是否启用基于请求头（`X-User-Id`）的兜底用户解析器，默认 `false`。该解析器无条件信任客户端传入的身份头，仅适用于前置网关已剥离外部身份头的内网服务间调用，开启时启动日志会输出 WARN。
  - `me.auth.propagate.allowed-hosts` — 认证头传播的目标主机白名单（精确主机名 / `*.example.com` 后缀通配 / `*`），默认空。未命中白名单时按服务名调用甄别放行：注册中心服务名（Spring Cloud LoadBalancer 可解析，经专用 SPI `IServiceInstanceProbe` 判定，业务可自定义覆盖）与单标签内网主机名默认放行，外部多标签域名/IP 一律不传播；`me.auth.propagate.service-discovery.enabled=false` 可关闭豁免回到严格白名单模式。
- **设计约定**：
  - 已纳入 `frame-me-boot`，业务 `xx-service` 引入 `frame-me-boot` 即可获得认证上下文能力。
  - **fail-closed**：容器中没有任何 `IAuthUserResolver` 实现且未开启 `me.auth.header-resolver.enabled` 时，`AuthFilter` 装配直接抛出带指引的异常（提示引入 auth-jwt / auth-sa-token 或显式开启 header-resolver），不会静默退化为不安全默认行为。
  - **仅 Servlet Web 应用装配**（`@ConditionalOnWebApplication(SERVLET)`）：非 Web 应用下整个模块退避。
  - `HeaderAuthUserResolver` 无条件信任 `X-User-Id` 头，仅限内网服务间调用；对外应用必须引入 `frame-me-starter-auth-jwt` 或 `frame-me-starter-auth-sa-token`（两者均 `@AutoConfigureBefore(AuthAutoConfiguration)`，先于抽象层注册解析器使其退避）。
  - 通过 `@AutoConfigureBefore(AuditAutoConfiguration.class)` 保证审计模块能拿到当前登录用户 ID。

## `frame-me-starter-auth-rbac`

- **定位**：框架无关的轻量 RBAC 授权模块，依赖 `frame-me-starter-auth`。提供 `@RequireAuth`(SpEL) 注解、方法拦截器、路径 Filter 与权限数据源 SPI；内置**数据权限**能力（SpEL 单条校验 + Service 静态 Helper 两种显式方式）；另内置可选的 Redis 权限后端（read-through 缓存，支持跨服务共享与吊销）。
- **依赖**：`frame-me-starter-auth`、`caffeine`（零传递叶子 jar，供 Redis 后端 L1 缓存）、`lombok`；`frame-me-starter-multi-redis` 为 **optional** 依赖——消费方显式引入 multi-redis 即激活 Redis 权限后端。
- **关键类**：
  - `com.frame.me.auth.rbac.config.RbacAutoConfiguration` — 自动装配入口，注册权限数据源插槽 `authPermissionSource`（默认配置版实现，业务声明任意 `IAuthPermissionProvider` bean 即退避）；仅 Servlet Web 应用装配（`@ConditionalOnWebApplication(SERVLET)`），非 Web 应用整体退避。
  - `com.frame.me.auth.rbac.config.RbacProperties` — `me.auth.permission.*` 配置属性绑定。
  - `com.frame.me.auth.rbac.annotation.RequireAuth` — 权限校验注解（SpEL 表达式）。
  - `com.frame.me.auth.rbac.permission.AuthExpressionRoot` — SpEL root，提供 `role()` / `perm()` 函数（表达式按字符串缓存）。
  - `com.frame.me.auth.rbac.permission.AuthPermissionHolder` — 请求级角色/权限 ThreadLocal 缓存（独立 `loaded` 标志；`ensureLoaded` 原子写入，provider 中途异常不留半加载状态）。
  - `com.frame.me.auth.rbac.permission.Permission` / `DataPermission` — 权限值对象。
  - `com.frame.me.auth.rbac.permission.IDataScopes` — 数据范围常量接口（`ALL`/`DEPT`/`ORG`/`SELF`/`CUSTOM`）。
  - `com.frame.me.auth.rbac.permission.DataPermissionResolver` — 数据权限合并语义（任一 `ALL` 放行；否则 scope/dataIds 并集），SpEL/Helper 两层统一委托。
  - `com.frame.me.auth.rbac.permission.AuthDataPermissions` — Service 层静态 Helper（`isAll`/`scopes`/`dataIds`/`check`）。
  - `com.frame.me.auth.rbac.permission.IAuthPermissionProvider` — 权限数据源 SPI。
  - `com.frame.me.auth.rbac.permission.ConfigAuthPermissionProvider` — 默认配置化权限提供者（roles/data-scopes 启动期 eager 解析，非法配置 fail-fast）。
  - `com.frame.me.auth.rbac.filter.PermissionFilter` — 路径规则权限过滤器；OPTIONS 预检请求带 `Origin` 头时直接放行，不参与权限校验（与 `AuthFilter` 同口径，无 `Origin` 非真预检走正常权限链防绕过）。
  - `com.frame.me.auth.rbac.interceptor.PermissionInterceptor` — `@RequireAuth` 注解权限拦截器；OPTIONS 预检请求带 `Origin` 头时直接放行，不参与权限校验（同上口径）。
  - `com.frame.me.auth.rbac.propagation.AuthPermissionTaskDecorator` — `@Async` 权限上下文传播装饰器。
  - `com.frame.me.auth.rbac.redis.config.RbacRedisAutoConfiguration` — 可选 Redis 后端装配入口（`@ConditionalOnClass(RedisUtils.class)` + `@AutoConfigureAfter(RbacAutoConfiguration.class)`，必须在插槽注册后处理）。
  - `com.frame.me.auth.rbac.redis.config.RbacRedisProperties` — `me.auth.permission.redis.*` 配置绑定。
  - `com.frame.me.auth.rbac.redis.RedisAuthPermissionProvider` — `@Primary` 权限提供者，L1 Caffeine → L2 Redis → 委托数据源 read-through，提供 `evict(userId)` 失效（L2 删除失败时异常抛给调用方，吊销可感知、可重试）。
  - `com.frame.me.auth.rbac.redis.UserPermissionSnapshot` — Redis 缓存的用户权限快照。
  - `com.frame.me.auth.rbac.redis.store.IPermissionCacheStore` / `RedisPermissionCacheStore` — 二级缓存存储 SPI 与 Redis 实现。契约：`get`/`set` 允许可用性降级（读失败回源、写失败下次重建），`delete` 服务权限吊销属安全动作，失败必须抛异常，不得静默降级。
- **自动装配**：通过 `frame-me-starter-auth-rbac/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `RbacAutoConfiguration`、`RbacRedisAutoConfiguration`。
- **可配置项**：
  - `me.auth.permission.enabled` — 是否启用权限控制，默认 `true`。**总开关，为 `false` 时 Redis 后端一并退避。**
  - `me.auth.permission.rules` — Filter 层「Ant 路径 → SpEL 表达式」映射。**YAML 中 key 必须用方括号记法** `"[/api/admin/**]"`，否则 relaxed binding 会剥离 `/`、`*` 导致规则静默失效（详见 `docs/conventions.md`）。按**应用内路径**匹配（不含 `server.servlet.context-path`）；规则 key 误带 context-path 前缀时自动剥离前缀平滑兼容。
  - `me.auth.permission.roles` — 角色到权限映射（逗号分隔 `resource:action`，action 可省略默认 `*`）。启动期 eager 解析，配置段 resource 为空直接启动 fail-fast（与 `data-scopes` 同标准），避免笔误静默变成永不匹配的死条目。
  - `me.auth.permission.users` — 用户 ID 到角色映射（逗号分隔）。
  - `me.auth.permission.propagate.async.enabled` — 是否传播权限上下文到 `@Async` 线程，默认 `true`。
  - `me.auth.permission.data-scopes` — 角色到数据范围映射（逗号分隔 `resource:SCOPE` 或 `resource:action:SCOPE`,action 省略默认 `*`;SCOPE 为 `ALL`/`DEPT`/`ORG`/`SELF`/`CUSTOM`）。格式或 SCOPE 非法时启动 fail-fast。动态 `dataIds` 仅自定义 provider 可提供。
  - `me.auth.permission.redis.enabled` — 是否启用 Redis 缓存层，默认 `true`（需 classpath 存在 multi-redis）。
  - `me.auth.permission.redis.keyPrefix` — 缓存 key 前缀，默认 `auth:perms:`。
  - `me.auth.permission.redis.clientName` — Redis 实例名（对应 `me.redis.clients`），默认 `default`。
  - `me.auth.permission.redis.redisTtl` / `localTtl` / `localMaxSize` — L2 Redis 过期、L1 本地过期与容量。
- **设计约定**：
  - RBAC 模块是可选依赖；Sa-Token / Spring Security 等自带 RBAC 的方案不需要引入此模块。
  - 未登录访问受保护接口返回 401，已登录无权限返回 403（统一 `Result`，HTTP 200、业务码在 body）。
  - `PermissionFilter` 在 `AuthFilter` 之后执行（`Ordered.HIGHEST_PRECEDENCE + 200`）。
  - **可选 Redis 后端**：显式引入 `frame-me-starter-multi-redis` 即激活（`@ConditionalOnClass(RedisUtils.class)`），`RedisAuthPermissionProvider` 以 `@Primary` 生效、配置版 provider 退避；不引入则仅配置版 provider，classpath 零 Redisson。激活后如需 caffeine 之外的调整见 `me.auth.permission.redis.*`。
  - 委托数据源默认 `ConfigAuthPermissionProvider`；声明名为 `authPermissionSource` 的 `IAuthPermissionProvider` bean 可接入数据库等真实数据源。
  - **bean 命名约束**：`RedisAuthPermissionProvider` 无条件 `@Primary`。业务自定义 provider 若作为数据源，必须命名为 `authPermissionSource` 且**不要**标 `@Primary`——否则会出现两个 `@Primary` 导致按类型注入处 `NoUniqueBeanDefinitionException`；启用 Redis 后端时业务 provider 若未命名为 `authPermissionSource`，默认插槽按类型退避后包装器按名注入失败，**启动 fail-fast**（不会静默忽略）。
  - 权限变更后调用 `RedisAuthPermissionProvider#evict(userId)` 失效缓存（L1 + L2）；读/写路径 Redis 异常自动降级回源，**`evict` 的 L2 删除失败会抛异常给调用方**（吊销可感知，重试即可收敛，见 `IPermissionCacheStore` 契约）。
  - **吊销最终一致**：`evict` 只清当前实例的 L1，其他实例的 L1 在 `localTtl`（默认 5s）内仍提供旧权限，即吊销最长延迟 = `localTtl`。需要即时生效的场景应调低 `localTtl`（趋近 0 即近似关闭 L1，读全部直连 Redis）。注意：直接清 Redis 只能清 L2，对其他实例 L1 无效。
  - **数据权限**：两种显式使用方式（`@RequireAuth("dataCheck('order', #id)")` 单条校验、`AuthDataPermissions` 静态 Helper）共享同一套合并语义（`DataPermissionResolver`）,SpEL 函数以 `data` 前缀标识数据权限域,实现委托 Helper 同名方法（`isAll`/`check`)。`dataIds` 语义统一为**资源行主键集合**，不是部门 ID 集合。历史曾有的「SQL 自动拦截」方式（MP/Flex 适配）已移除——两个 ORM 能力不对等、fail-open 边界多；列表过滤统一用 Helper 显式拼条件，注意事项见 `docs/conventions.md` 数据权限小节。
  - **Redis 快照升级注意**：`UserPermissionSnapshot` 新增 `dataPermissions` 字段后，升级前写入的旧 JSON 反序列化得空列表——升级后数据权限为空直至 TTL 过期或 `evict`，需要立即生效时重启后 `evict` 受影响用户。
  - **测试注意**：本模块 test classpath 带 redisson 系（multi-redis 在自身 classpath 上），未来在本模块内写全量 `@SpringBootTest` 可能触发 Redisson 装配连接 Redis，应优先用 `ApplicationContextRunner` 切片测试。
  - 所有服务共享同一 Redis 时 RBAC 判定天然一致，适合多服务统一权限语义。

## `frame-me-starter-auth-jwt`

- **定位**：基于 JWT 的认证实现 starter，完全接管 `frame-me-starter-auth` 的 `IAuthService` / `IAuthUserResolver`，提供登录/登出/刷新/当前用户接口。
- **依赖**：`frame-me-starter-auth`、`jjwt-api`、`jjwt-impl`（runtime）、`jjwt-gson`（runtime）、`spring-security-crypto`、`lombok`；`frame-me-starter-multi-redis` 为 optional 依赖，用于 Refresh Token 持久化。JSON 序列化适配用 `jjwt-gson` 而非 `jjwt-jackson`，避免引入 Jackson 2 与 Boot 4 的 Jackson 3（`tools.jackson`）并存；jjwt 0.13.0 尚无 Jackson 3 适配器。
- **关键类**：
  - `com.frame.me.auth.jwt.config.JwtAutoConfiguration` — 自动装配入口，通过 `@AutoConfigureBefore(AuthAutoConfiguration.class)` 保证优先于 auth 抽象层加载。
  - `com.frame.me.auth.jwt.config.JwtAuthProperties` — `me.auth.jwt.*` 配置属性绑定。
  - `com.frame.me.auth.jwt.core.JwtTokenService` — `IAuthService` 实现，负责 Access/Refresh Token 生成、解析与刷新。
  - `com.frame.me.auth.jwt.core.JwtAuthUserResolver` — `IAuthUserResolver` 实现，从 `Authorization: Bearer ...` 解析当前用户。
  - `com.frame.me.auth.spi.IAuthUserDetailsService` — **位于抽象层**：业务需实现的接口，按账号/ID 查询用户、校验密码（与 Sa-Token 实现共用）。
  - `com.frame.me.auth.jwt.core.IRefreshTokenStore` / `RedisRefreshTokenStore` — Refresh Token 存储抽象与默认 Redis 实现。
  - `com.frame.me.auth.jwt.web.JwtAuthController` — 默认认证接口：登录/登出/刷新/当前用户/管理员强制登出；基础路径默认 `/api/auth`，可通过 `me.auth.jwt.path` 修改。
  - `com.frame.me.auth.web.dto.LoginDTO` / `com.frame.me.auth.web.vo.TokenVO` — **位于抽象层**：登录请求与 Token 响应（与 Sa-Token 实现共用）。
  - `com.frame.me.auth.util.PasswordUtils` — **位于抽象层**：BCrypt 密码加解密工具。
- **自动装配**：通过 `frame-me-starter-auth-jwt/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `JwtAutoConfiguration`。
- **可配置项**：
  - `me.auth.jwt.enabled` — 是否启用，默认 `true`。
  - `me.auth.jwt.secret` — JWT 签名密钥，**必须配置**，长度不少于 32 字符。启动期校验：未配置/为空白直接启动 fail-fast，弱密钥（不满足 HS 系列强度）同样在启动期抛出，不会延迟到首次请求。
  - `me.auth.jwt.issuer` — 签发者，默认 `me`。签发端写入 `iss` claim，解析端用 `requireIssuer` 校验：iss 不匹配的 token 一律拒绝（validate 返回 false / getUser 返回 null / refresh/logout 抛 401）。防护多服务/多环境共用同一 secret 时 token 跨签发者穿透；issuer 不匹配抛 `IncorrectClaimException`，已被各解析方法的 catch 覆盖不会逃逸。
  - `me.auth.jwt.access-token-expires` — Access Token 有效期，默认 `PT2H`。
  - `me.auth.jwt.refresh-token-expires` — Refresh Token 有效期，默认 `P7D`。
  - `me.auth.jwt.token-header` — Token 请求头，默认 `Authorization`。鉴权解析、logout、refresh 全部共用此配置源（refresh 不硬编码 `Authorization`）。
  - `me.auth.jwt.token-prefix` — Token 前缀，默认 `Bearer `。按 RFC 6750 §2.1 前缀大小写不敏感（`Bearer`/`bearer`/`BEARER` 均接受），`JwtTokenService.extractToken` 与 `JwtAuthController.extractToken` 用 `regionMatches(true, ...)` 忽略大小写匹配剥离。
  - `me.auth.jwt.cookie-domain` — Refresh Token Cookie 的 Domain。未配置时 JSON 返回 Refresh Token；配置后（如 `.example.com`）将 Refresh Token 作为 `HttpOnly`/`Secure`/`SameSite=Lax` Cookie 下发，JSON 中不再返回，且 `refresh`/`logout` 自动读写/清除 Cookie。
  - `me.auth.jwt.path` — JWT 认证接口基础路径，默认 `/api/auth`；配置后登录/登出/刷新/当前用户接口均迁移到该路径下。
- **设计约定**：
  - **不纳入 `frame-me-boot`**，业务 `xx-service` 需显式引入。
  - 业务只需实现抽象层 `com.frame.me.auth.spi.IAuthUserDetailsService`，即可自动获得 JWT 登录能力。
  - Access Token 为无状态 JWT；Refresh Token 存 Redis，支持登出失效。登出接受已过期的 Access / Refresh Token（jjwt 验签后 claims 可信，仅 logout 场景放宽时效），保证过期后登出仍能清除 Refresh Token；`validate` / `getUser` / `refresh` 语义不变，过期即无效。
  - **refresh 的异常语义**：凭证问题（格式非法/签名不符/类型错误/已过期）返回 401；`IRefreshTokenStore` 等基础设施故障（如 Redis 连接异常）不吞成 401，原样上抛由全局异常处理映射 5xx，避免客户端误以为凭证失效而走重新登录。
  - Refresh Token 存储后端：`frame-me-starter-multi-redis` 为 optional 依赖，显式引入即激活 `RedisRefreshTokenStore`；未引入时回退为 `InMemoryRefreshTokenStore`（单实例可用，装配时打 WARN；多实例部署 refresh/强制登出不跨实例生效，必须引入 multi-redis）。业务可注册自定义 `IRefreshTokenStore` 覆盖两者。
  - 提供管理员强制登出接口 `POST /admin/logout/{userId}`，清除该用户的 Refresh Token（已颁发的 Access Token 在自然过期前仍有效）；**默认关闭**，需通过 `me.auth.admin.logout.enabled=true` 开启，开启后必须自行配置访问控制（`me.auth.permission.rules` 或自定义拦截器）。

## `frame-me-starter-auth-sa-token`

- **定位**：基于 sa-token（`cn.dev33:sa-token-spring-boot4-starter`，1.45.0）的会话治理型认证 starter，接管 `frame-me-starter-auth` 的 `IAuthService` / `IAuthUserResolver`。面向需要踢人 / 封禁 / 在线会话 / 多端互斥的后台场景，是 `frame-me-starter-auth-jwt`（+ rbac）之外的可选认证实现。
- **依赖**：`frame-me-starter-auth`、`sa-token-spring-boot4-starter`、`fastjson2`、`spring-security-crypto`、`lombok`；`frame-me-starter-multi-redis` 为 optional 依赖——消费方显式引入即激活 Redis 会话后端。
- **关键类**：
  - `com.frame.me.auth.satoken.config.SaTokenAuthAutoConfiguration` — 自动装配入口（`@AutoConfigureBefore(AuthAutoConfiguration.class)`）：接管 `IAuthService` / `IAuthUserResolver`，注册配置版 `StpInterface`、默认 Controller、异常 Advice 与 `SaInterceptor`（路径规则 + `@SaCheck*` 注解鉴权）。`SaInterceptor` 的 auth 回调对带 `Origin` 头的 OPTIONS 预检请求直接跳过规则校验，避免预检被鉴权拦截；无 `Origin` 的 OPTIONS 非真预检，走正常鉴权链防绕过（与 `AuthFilter` 同口径）。sa-token 原生 `SaTokenConfig` 由官方 starter 的 `SaBeanRegister` 绑定 `sa-token.*` 配置路径提供，本模块不声明。
  - `com.frame.me.auth.satoken.config.SaTokenAuthProperties` — `me.auth.sa-token.*` 配置属性绑定；启动时校验 rules key 是否以 `/` 开头，防 relaxed binding 导致的规则静默失效。
  - `com.frame.me.auth.satoken.config.SaTokenRedisDaoAutoConfiguration` — Redis 会话后端装配入口（类级 `@ConditionalOnClass(RedisUtils.class)`，与总开关 `me.auth.sa-token.enabled` 及 `me.auth.sa-token.redis.enabled` 联动）。
  - `com.frame.me.auth.satoken.config.SaTokenNoRedisWarnAutoConfiguration` — multi-redis 缺席告警（`@ConditionalOnMissingClass`，提示会话退回内存存储）。
  - `com.frame.me.auth.satoken.core.SaTokenAuthService` — `IAuthService` 实现：`login` / `logout` 走 sa-token 标准上下文 API（`StpUtil.login/logout`，原生写 / 清 Cookie），面向请求线程调用；`refresh` 续期目标为传入 credential（`renewTimeout(token, timeout)` 带参重载），不依赖上下文 token，token 值不变故 Cookie 无需重写；`validate` / `getUser` 用 `getLoginIdByToken` 纯 DAO 查询，上下文无关。用户快照以 JSON 写入 Account-Session，读取缓存优先、回源 `IAuthUserDetailsService#loadUserById`；Session 读取用 no-create 重载，读路径不产生写副作用（session 缺失时不重建、跳过缓存回写，直接回源）。
  - `com.frame.me.auth.satoken.core.SaTokenAuthUserResolver` — `IAuthUserResolver` 实现：显式从原生 `sa-token.token-name` 指定的请求头读 token（头名取 `SaManager.getConfig().getTokenName()`），header 缺失时按同名 Cookie 兜底读取（遵循原生 `sa-token.is-read-cookie` 开关，显式关闭后本框架同样不读 Cookie）。静态入口 `extractToken` 是 Controller（logout/refresh）与 Resolver 共用的唯一提取方法，Cookie-only 客户端两个端点均可正常工作。刻意不用 `StpUtil.getTokenValue()`——框架 `AuthFilter`（order = HIGHEST_PRECEDENCE + 100）先于官方 `SaTokenContextFilter`（order = -104）执行，此时 sa-token 上下文尚未初始化。
  - `com.frame.me.auth.satoken.core.SaTokenRuleEvaluator` — 路径规则简化表达式求值器（非 SpEL）：`login` / `role:xxx` / `perm:resource` / `perm:resource:action`；非法表达式在装配期预解析直接启动失败。
  - `com.frame.me.auth.satoken.core.RedisSaTokenDao` — 基于 `RedisUtils` 的 `SaTokenDao` 实现（`SaTokenDaoByObjectFollowString`），timeout 分支语义逐条对齐官方 `SaTokenDaoForRedisTemplate`；所有 key 按 sa-token 传入值原样读写（sa-token 生成的 key 自带 tokenName 前缀，不再叠加命名空间），`clientName` 路由多实例。
  - `com.frame.me.auth.satoken.permission.ConfigStpInterface` — 配置版权限数据源（sa-token 原生 RBAC）：从 `me.auth.sa-token.users` / `roles` 读取角色与权限码（原样透传），构造期一次性预解析 CSV，运行期仅 map 查找；业务声明任意 `StpInterface` Bean 即接管（本实现退避）。
  - `com.frame.me.auth.satoken.web.SaTokenAuthController` — 默认认证接口：登录 / 登出 / 续期 / 当前用户 / 管理员强制登出，基础路径默认 `/api/auth`（`me.auth.sa-token.path` 可改）；登录请求与 Token 响应复用抽象层 `com.frame.me.auth.web.dto.LoginDTO` / `com.frame.me.auth.web.vo.TokenVO`（sa-token 会话模型无 Refresh Token 概念，`refreshToken` 恒为 `null`）。
  - `com.frame.me.auth.satoken.advice.SaTokenExceptionAdvice` — sa-token 异常到 401/403 语义的映射（`@Order(HIGHEST_PRECEDENCE)`，先于 `GlobalExceptionHandler` 的通用处理器）。
- **自动装配**：通过 `frame-me-starter-auth-sa-token/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `SaTokenAuthAutoConfiguration`、`SaTokenRedisDaoAutoConfiguration`、`SaTokenNoRedisWarnAutoConfiguration`。
- **可配置项**：sa-token 原生参数（`token-name` / `timeout` / `active-timeout` / `is-concurrent` / `is-share` / `cookie.*` 等）走官方 `sa-token.*` 配置路径（秒数 long 形式，见 sa-token 官方文档），由官方 starter 的 `SaBeanRegister` 绑定；本模块 `me.auth.sa-token.*` 仅承载框架自有配置（默认值以 `SaTokenAuthProperties` 源码为准）：
  - `me.auth.sa-token.enabled` — 是否启用 Sa-Token 认证，默认 `true`；为 `false` 时 Redis 会话后端配置一并退避。
  - `me.auth.sa-token.path` — 认证接口基础路径，默认 `/api/auth`；配置后登录/登出/续期/当前用户接口均迁移到该路径下。
  - `me.auth.sa-token.rules` — Interceptor 层「Ant 路径 → 简化鉴权表达式」映射。**YAML 中 key 必须用方括号记法** `"[/api/admin/**]"`，否则 relaxed binding 剥离 `/`、`*` 导致规则匹配不上；启动时校验到不以 `/` 开头的 key 会直接抛异常，阻止应用启动（fail-fast）。
  - `me.auth.sa-token.authorization.enabled` — 是否启用 sa-token 原生鉴权能力（路径规则 + `@SaCheck*` 注解），默认 `true`；关闭后仍保留登录/登出/续期/强制登出/踢人/在线会话等认证与会话治理能力，可把鉴权交给 RBAC 等其它模块。
  - `me.auth.sa-token.jwt.enabled` — 是否启用 JWT Token 模式，默认 `false`；开启后 `StpUtil.login(...)` 颁发的 Token 会变为 JWT 格式（仍使用 `StpLogicJwtForSimple` 的 Simple 模式，会话数据继续存 Redis，保留踢人/在线会话能力），便于网关独立验签。启用后需在业务工程显式引入 `sa-token-jwt` 并配置 `sa-token.jwt-secret-key`。
  - `me.auth.sa-token.roles` — 角色到权限码映射（逗号分隔 `resource:action` 或 `resource`，原样透传）。
  - `me.auth.sa-token.users` — 用户 ID（字符串）到角色映射（逗号分隔）。
  - `me.auth.sa-token.redis.enabled` — 是否启用 Redis 会话存储，默认 `true`（需 classpath 存在 `frame-me-starter-multi-redis`）。
  - `me.auth.sa-token.redis.client-name` — Redis 实例名（对应 `me.redis.clients` 的 key），默认 `default`。
- **设计约定**：
  - **不纳入 `frame-me-boot`**，业务 `xx-service` 需显式引入。
  - 业务接入只需实现抽象层 `com.frame.me.auth.spi.IAuthUserDetailsService`，与 JWT 实现共用同一份业务实现。
  - 当前只做不透明 token 的会话模式（Redis / 内存），代码不假设 token 格式；`StpLogicJwtForSimple`（jwt-simple）留口——未来接入只需替换 `StpUtil.setStpLogic(...)`，`SaTokenAuthService` 无需改动。
  - multi-redis 缺席时退回 sa-token 内存 DAO（单实例可用），启动时 WARN 提示；多实例部署必须引入 `frame-me-starter-multi-redis` 以共享会话。
  - 权限数据源默认配置版；业务声明 `StpInterface` Bean 接管时，接数据库的实现应自行做缓存——sa-token 每次鉴权都会回调该接口。
  - 未登录 / Token 失效返回 401，无角色 / 无权限 / 被封禁返回 403：Filter 层 401 仍由 `AuthFilter` + `IFilterErrorResponseWriter` 输出；注解与路径规则层异常由 `SaTokenExceptionAdvice` 映射。
  - **无数据权限**：不提供行级数据范围能力，需要数据权限的项目应选择 `frame-me-starter-auth-rbac`。
  - 与 JWT 的语义映射：`sa-token.timeout` ≈ refresh token 绝对期，`sa-token.active-timeout` ≈ access token 闲置窗口，`/refresh` ≈ `renewTimeout` 续期（原 token 不变）；`TokenVO.refreshToken` 恒 `null`。
  - 提供管理员强制登出接口 `POST /admin/logout/{userId}`，调用 `StpUtil.logout(loginId)` 踢出用户所有会话（Redis 后端下全节点即时生效）；**默认关闭**，需通过 `me.auth.admin.logout.enabled=true` 开启，开启后必须自行配置访问控制（`me.auth.sa-token.rules` 或引入 `frame-me-starter-auth-rbac` 后配置 `me.auth.permission.rules`）。
  - **原生 Cookie 支持**：`sa-token.is-read-cookie` 默认 `true`——`StpUtil.login()` 原生写 Cookie、`logout()` 原生清、`renewTimeout()` 原生刷；Cookie 名取 `sa-token.token-name`，属性全部来自 `sa-token.cookie.*`（domain/path/secure/http-only/same-site），Max-Age 由 `is-lasting-cookie` + `timeout` 派生。Token 同时永远经 JSON body 返回，前端 header / Cookie 双通道二选一；Resolver 的 Cookie 兜底读取与原生行为对齐。

## `frame-me-starter-cloud`

- **定位**：微服务云组件模块（当前为占位）。
- **依赖**：`frame-me-starter-base`、`lombok`。
- **关键类**：
  - `com.frame.me.cloud.CloudConstant` — 占位常量类。
- **扩展提示**：未来可引入 Nacos 注册/配置中心、Gateway、Sentinel、分布式链路追踪等。

## `frame-me-starter-multi-redis`

- **定位**：Redis 基础能力 starter，封装 `spring-boot-starter-data-redis` 与统一操作工具 `RedisUtils`；在引入 Redisson 时自动启用 Redisson 高阶能力。
- **依赖**：`frame-me-starter-base`、`spring-boot-starter-data-redis`、`fastjson2`、`lombok`；`redisson` 为 optional 依赖。
- **关键类**：
  - `com.frame.me.redis.config.RedisAutoConfiguration` — Spring Data Redis 自动装配入口，创建 `StringRedisTemplate` / `RedisTemplate` 并初始化 `RedisUtils`；额外实例（`me.redis.clients.*`）的 `LettuceConnectionFactory` 由本类管理生命周期（实现 `DisposableBean`，容器关闭时销毁，避免连接泄漏）。
  - `com.frame.me.redis.config.RedisProperties` — `me.redis` 配置属性绑定（多实例、开关等）。
  - `com.frame.me.redis.config.RedissonLockAutoConfiguration` — Redisson 自动装配入口，创建 `RedissonClient` 并初始化所有 Redisson 工具类；`meRedissonClient` 标 `@ConditionalOnMissingBean(RedissonClient.class)`，业务自定义 RedissonClient 时自动退避。
  - `com.frame.me.redis.config.RedissonProperties` — `spring.data.redis.redisson` 配置属性绑定。
  - `com.frame.me.redis.util.RedisUtils` — 统一 Redis 操作工具，支持 String、Hash、List、Set、ZSet、计数、简单分布式锁等。
  - `com.frame.me.redis.util.RedisClient` — 单实例 Redis 操作封装，供 `RedisUtils` 委托。
  - `com.frame.me.redis.util.RedissonLock` — Redisson 可重入锁静态入口（需引入 Redisson），支持看门狗续期。
  - `com.frame.me.redis.util.RedissonSync` — Redisson 同步原语：读写锁、公平锁、联锁、信号量、倒计时门闩、可过期信号量（红锁已随 Redisson 4.x 弃用）。
  - `com.frame.me.redis.util.RedissonTopic` — Redisson 消息能力：Topic、PatternTopic、ReliableTopic、Stream。
  - `com.frame.me.redis.util.RedissonLimiter` — Redisson 限流：基于 `RRateLimiter` 的令牌桶限流。
  - `com.frame.me.redis.RedisConstant` — 占位常量类。
- **自动装配**：通过 `frame-me-starter-multi-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `RedisAutoConfiguration`、`RedissonLockAutoConfiguration`。
- **启用条件**：
  - `RedisAutoConfiguration`：类路径存在 `StringRedisTemplate`，`me.redis.enabled=true`（默认开启，可显式关闭）。
  - `RedissonLockAutoConfiguration`：类路径存在 `org.redisson.api.RedissonClient`，`me.redis.enabled=true`（默认开启）。
- **可配置项**：
  - `me.redis.enabled` — 是否启用 Redis 自动配置，默认 `true`。
  - `me.redis.clients` — 多 Redis 实例配置，key 为实例名；默认实例仍由 `spring.data.redis.*` 提供。
  - `me.redis.clients.*.mode` — 部署模式（`standalone` / `cluster` / `sentinel`），默认 `standalone`。
  - `me.redis.clients.*.host` — 主机地址（`standalone` 模式），默认 `localhost`。
  - `me.redis.clients.*.port` — 端口（`standalone` 模式），默认 `6379`。
  - `me.redis.clients.*.nodes` — 节点列表，格式 `host:port`（`cluster` / `sentinel` 模式）。
  - `me.redis.clients.*.sentinel-master` — 哨兵监控的主节点名称（`sentinel` 模式）。
  - `me.redis.clients.*.username` — 用户名（Redis 6.0+ ACL）。
  - `me.redis.clients.*.password` — 密码。
  - `me.redis.clients.*.database` — 数据库索引（`cluster` 模式不支持，将被忽略），默认 `0`。
  - `spring.data.redis.redisson.config` — Redisson 原生配置文件位置（支持 `classpath:` / `file:` 前缀），可选。
- **使用方式**：
  - 直接调用 `RedisUtils.xxx()` 使用 Spring Data Redis 能力。
  - 分布式锁默认为简单实现（`SET NX PX` + Lua 释放），不含看门狗续期；引入 Redisson 后自动启用 `RedissonLock`，提供可重入与看门狗续期。
  - Redisson 连接配置优先级：配 `spring.data.redis.redisson.config=classpath:redisson.yaml`（与 `redisson-spring-boot-starter` 标准配置项对齐）时用 Redisson 原生 YAML（支持全部 5 种模式，含 masterSlave/replicated）；否则自动复用 `spring.data.redis.*`——配 `cluster.nodes` 走集群、配 `sentinel.master/nodes` 走哨兵，否则单机，无需额外配置。哨兵模式仅配 `master` 未配 `nodes` 时启动 fail-fast，提示"哨兵模式必须提供至少一个节点"。
  - 用户名/密码在 Redisson 4.x 中需在顶层 `Config` 对象上设置，本 starter 已通过 `Config.setUsername` / `Config.setPassword` 实现。
- **设计约定**：
  - 已纳入 `frame-me-boot`，业务 `xx-service` 引入 `frame-me-boot` 即可获得 Redis 能力。
  - Hash 写入口（`hSet` / `hSetAll`）统一把值 JSON 序列化后存储，与 `hGet(key, hashKey, clazz)` 的反序列化对称；不要混用原生 `putAll` 绕过该约定。
  - Redisson 为 optional 依赖，未引入时不影响 `RedisUtils` 使用。
  - 各 Redisson 工具类采用 `final` + 静态 `init(RedissonClient)` 模式，未初始化时调用会抛出 `IllegalStateException`。

**示例配置**：

```yaml
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

## `frame-me-starter-sse-mvc`

- **定位**：SSE 服务端推送 starter，支持按事件类型广播与按接收者 ID 定向推送。
- **依赖**：`frame-me-starter-base`、`spring-boot-starter-web`、`fastjson2`、`lombok`。
- **关键类**：
  - `com.frame.me.sse.mvc.core.SseEmitterManager` — Emitter 生命周期与路由管理；提供 `heartbeat()` 发送 SSE comment 保活并清理失败连接。
  - `com.frame.me.sse.mvc.core.SseEventDispatcher` — 监听 `MeApplicationEvent` 并转发到 SSE。
  - `com.frame.me.sse.mvc.service.SsePushService` — 业务推送 API。
  - `com.frame.me.sse.mvc.web.SseController` — SSE 订阅端点。
  - `com.frame.me.sse.mvc.config.SseAutoConfiguration` — 自动装配入口。
  - `com.frame.me.sse.mvc.config.SseProperties` — `me.sse` 配置属性绑定。
  - `com.frame.me.sse.mvc.config.SseHeartbeatTask` — 可选心跳任务（`me.sse.heartbeat-interval > 0` 时装配）。
  - `com.frame.me.sse.mvc.SseConstant` — 常量。
- **自动装配**：通过 `frame-me-starter-sse-mvc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `SseAutoConfiguration`。
- **启用条件**：
  - Servlet Web 应用。
  - `me.sse.enabled=true`（默认 true，可显式关闭）。
- **可配置项**：
  - `me.sse.enabled` — 是否启用 SSE，默认 `true`。
  - `me.sse.path` — SSE 订阅接口基础路径，默认 `/api/sse`。
  - `me.sse.timeout` — `SseEmitter` 超时时间（毫秒），`0` 表示不超时，默认 `0`。
  - `me.sse.retry` — 客户端重连间隔（毫秒），订阅建立时以 `retry:` 指令发送给客户端，默认 `3000`。
  - `me.sse.heartbeat-interval` — 心跳间隔（秒），大于 0 时启用调度并发送 SSE comment 保活、探测死连接，默认 `0`（不发）。
  - `me.sse.broadcast-enabled` — 是否自动把 `MeApplicationEvent` 广播到 SSE，默认 `true`。
  - `me.sse.targeted-enabled` — 是否启用定向订阅，默认 `true`。
  - 两个开关任一开启即装配 `SseEventDispatcher`（分发时按开关拦截对应分支）；仅当两者同时关闭才不装配。
  - `me.sse.max-emitters` — 单服务实例最大并发 Emitter 数，默认 `1000`（`0` 表示不限制）；每个长连接占用容器线程与句柄，无上限可被 DoS，超限返回 429。
- **使用方式**：
  - 广播订阅：`GET {path}/subscribe/{eventType}`（默认 `/api/sse/subscribe/{eventType}`）。
  - 定向订阅：`GET {path}/subscribe?receiverId={receiverId}`（默认 `/api/sse/subscribe?receiverId={receiverId}`）。
  - 业务推送：注入 `SsePushService` 调用 `broadcast` / `pushToReceiver`。
  - 自动转发：发布 `MeApplicationEvent` 后，订阅该事件类型的 SSE 客户端自动收到。
- **设计约定**：
  - 已纳入 `frame-me-boot`，业务 `xx-service` 引入 `frame-me-boot` 即可获得 SSE 能力（`me.sse.enabled=false` 可关闭）。
  - 定向推送仅在**当前服务实例**内生效，跨实例需要额外的分布式路由层。
  - 无离线补偿，客户端断线期间消息直接丢弃。
  - `eventType` / `receiverId` 做长度（≤128）与字符白名单（字母数字、冒号、下划线、短横）校验，非法返回 400，防恶意 key 撑爆路由表。
  - 定向订阅 `receiverId` 归属校验：注册 `IReceiverIdAuthorizer`（`com.frame.me.base.event`）Bean 后按登录身份校验，失败返回 403；未注册则不校验（starter 不绑定具体鉴权方案），应由业务用 auth 路径规则或前置 Filter/拦截器保护。
  - 默认 `timeout=0`（不超时）时，半关闭连接依赖 `me.sse.heartbeat-interval > 0` 的心跳探测清理；生产建议开启心跳或设置有限 timeout。

**示例配置**：

```yaml
me:
  sse:
    enabled: true
    timeout: 0
    retry: 3000
    heartbeat-interval: 30        # 生产建议开启，探测半关闭连接
    broadcast-enabled: true
    targeted-enabled: true
    max-emitters: 10000
```

## `frame-me-starter-ws-mvc`

- **定位**：Servlet 原生 WebSocket starter，支持按事件类型广播与按接收者 ID 定向推送，提供全双工通道。
- **依赖**：`frame-me-starter-base`、`spring-boot-starter-websocket`、`fastjson2`、`lombok`。
- **关键类**：
  - `com.frame.me.ws.mvc.core.WsMvcSessionManager` — WebSocketSession 生命周期与路由管理。
  - `com.frame.me.ws.mvc.core.WsMvcEventDispatcher` — 监听 `MeApplicationEvent` 并转发到 WebSocket。
  - `com.frame.me.ws.mvc.service.WsMvcPushService` — 业务推送 API。
  - `com.frame.me.ws.mvc.handler.MeWsMvcHandler` — WebSocket 连接与消息处理。
  - `com.frame.me.ws.mvc.config.WsMvcAutoConfiguration` — 自动装配入口（类 Javadoc 记录 WebFlux WebSocket / STOMP / RSocket 后续扩展方向）。
  - `com.frame.me.ws.mvc.config.WsMvcProperties` — `me.ws.mvc` 配置属性绑定。
  - `com.frame.me.ws.mvc.config.WsMvcHeartbeatTask` — 可选心跳任务。
  - `com.frame.me.ws.mvc.WsMvcConstant` — 常量。
- **自动装配**：通过 `frame-me-starter-ws-mvc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `WsMvcAutoConfiguration`。
- **启用条件**：
  - Servlet Web 应用。
  - `me.ws.mvc.enabled=true`（默认 true，可显式关闭）。
- **可配置项**：
  - `me.ws.mvc.enabled` — 是否启用 WebSocket MVC，默认 `true`。
  - `me.ws.mvc.path` — WebSocket 端点路径，默认 `/api/ws`。
  - `me.ws.mvc.broadcast-enabled` — 是否自动广播 `MeApplicationEvent`，默认 `true`。
  - `me.ws.mvc.targeted-enabled` — 是否启用定向订阅，默认 `true`。
  - 两个开关任一开启即装配 `WsMvcEventDispatcher`（分发时按开关拦截对应分支）；仅当两者同时关闭才不装配。
  - `me.ws.mvc.max-sessions` — 单服务实例最大并发 session 数，默认 `1000`（`0` 表示不限制）；每个长连接占用内存与发送线程，无上限可被 DoS，超限的新连接以 `POLICY_VIOLATION` 关闭。
  - `me.ws.mvc.heartbeat-interval` — 心跳间隔（秒），`0` 表示不发送心跳，默认 `30`。
  - `me.ws.mvc.scheduling-enabled` — 是否启用调度支持（含心跳任务），默认 `true`；设为 `false` 时不加载 `@EnableScheduling`，也不会创建 `WsMvcHeartbeatTask`。
  - `me.ws.mvc.allowed-origins` — 握手允许的 Origins，默认空列表=**仅允许同源连接**（防跨站 WebSocket 劫持 CSWSH）；跨域访问必须显式配置可信源白名单，`["*"]` 全开放仅限开发环境（会打 WARN）。**行为变更**：旧版本空值等价于 `*`（允许所有源），未显式配置的跨域 WS 客户端升级后会握手失败，迁移方式即显式配置本项。
  - `me.ws.mvc.send-time-limit` — 单 session 发送最长耗时（毫秒），超时关闭该 session，默认 `10000`。
  - `me.ws.mvc.buffer-size-limit` — 单 session 发送缓冲上限（字节），超过关闭该 session，默认 `65536`。
- **使用方式**：
  - 广播订阅：`ws://host{path}?type=broadcast&eventType=user:created`（默认 `/api/ws?type=broadcast&eventType=user:created`）。
  - 定向订阅：`ws://host{path}?type=targeted&receiverId=user:123`（默认 `/api/ws?type=targeted&receiverId=user:123`）。
  - 业务推送：注入 `WsMvcPushService` 调用 `broadcast` / `pushToReceiver`。
  - 自动转发：发布 `MeApplicationEvent` 后，订阅该事件类型的 WebSocket 客户端自动收到。
- **设计约定**：
  - **不纳入 `frame-me-boot`**，由业务 `xx-service` 按需引入。
  - session 注册时以 `ConcurrentWebSocketSessionDecorator` 包装一次并统一持有装饰实例：心跳、广播、pong 等多线程发送由此串行化（避免帧交错），慢客户端受 `send-time-limit` / `buffer-size-limit` 防护；移除按 session id 匹配，原始 session 与装饰实例均可传入。
  - session/emitter 移除用 `ConcurrentHashMap.compute` 原子完成"移除元素 + 判空移除 key"，避免并发注册复用被清空的空集合而丢失连接。
  - 定向推送仅在**当前服务实例**内生效，跨实例需要额外的分布式路由层。
  - 无离线补偿，客户端断线期间消息直接丢弃。
  - `eventType` / `receiverId` 做长度（≤128）与字符白名单（字母数字、冒号、下划线、短横）校验，非法以 `BAD_DATA` 关闭连接，防恶意 key 撑爆路由表。
  - 定向订阅 `receiverId` 归属校验：注册 `IReceiverIdAuthorizer`（`com.frame.me.base.event`）Bean 后按登录身份校验，失败以 `BAD_DATA` 关闭连接；未注册则不校验，由业务通过 auth 路径规则或 `HandshakeInterceptor` 自行保护。
  - `me.ws.mvc.scheduling-enabled=false` 会关闭本模块的 `@EnableScheduling`：若业务工程的 `@Scheduled` 任务依赖此处开启的调度支持，需自行保证 `@EnableScheduling` 存在（或引入 base 的 `SchedulingAutoConfiguration`）。
  - 后续可扩展 `frame-me-starter-ws-webflux`（WebFlux 原生 WebSocket）、`frame-me-starter-ws-stomp`（Servlet STOMP）、`frame-me-starter-rsocket`（RSocket），路径与 auto-config 条件均与本模块不冲突。

**示例配置**：

```yaml
me:
  ws:
    mvc:
      enabled: true
      path: /api/ws
      broadcast-enabled: true
      targeted-enabled: true
      max-sessions: 10000        # 显式调大，默认 1000
      heartbeat-interval: 30
      scheduling-enabled: true
      allowed-origins: []        # 空=仅同源；跨域须显式列可信源，["*"] 仅限开发
      send-time-limit: 10000
      buffer-size-limit: 65536
```

## `frame-me-starter-l1l2-cache`

- **定位**：两级缓存 starter，基于 JetCache 提供 Caffeine（L1）+ Redis（L2）缓存能力。
- **依赖**：`jetcache-starter-redis-lettuce`、`caffeine`、`lombok`。
- **关键类**：
  - `com.frame.me.cache.config.CacheAutoConfiguration` — 自动装配入口，启用方法级缓存注解。
  - `com.frame.me.cache.config.CacheProperties` — `me.cache` 配置属性绑定。
  - `com.frame.me.cache.config.JetCacheInfrastructureRoleFixer` — 修复 JetCache 内部配置类的 BeanPostProcessor 警告。
  - `com.frame.me.cache.CacheConstant` — 占位常量类。
- **自动装配**：通过 `frame-me-starter-l1l2-cache/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `CacheAutoConfiguration`。
- **启用条件**：
  - 类路径存在 JetCache 核心类。
  - `me.cache.enabled=true`（默认关闭）。
- **使用方式**：
  - 在 Service 方法上标注 `@Cached(name = "...", cacheType = CacheType.BOTH)` 启用两级缓存。
  - 使用 `@CacheInvalidate` 在写操作时使缓存失效。
  - 使用 `@CacheUpdate` 在更新操作时更新缓存。
- **设计约定**：
  - 已纳入 `frame-me-boot`，业务 `xx-service` 引入 `frame-me-boot` 即可获得缓存能力。
  - 默认关闭，需显式开启。
  - 缓存连接参数通过 `jetcache.*` 原生属性控制。
  - 当前使用 `java` 序列化作为 value encoder，要求缓存对象实现 `Serializable`。

**示例配置**：

```yaml
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
  - `com.frame.me.encrypt.env.EncryptablePropertyEnvironmentPostProcessor` — 实现 Boot 4 的 `org.springframework.boot.EnvironmentPostProcessor`，扫描属性源解密 `ME(...)`；含密文的属性源原位替换为 `DecryptedPropertySource`（读取时解密），不新增属性源、不改变优先级链。
  - `com.frame.me.encrypt.env.DecryptedPropertySource` — 原位解密包装器：读取时对 `ME(...)` 值即时解密（按 key 缓存），保证命令行 / JVM -D / 环境变量等更高优先级来源仍可覆盖加密配置项（生产紧急切换场景）。
  - `com.frame.me.encrypt.util.JasyptEncryptor` — 统一构建 `StandardPBEStringEncryptor`（默认 `PBEWITHHMACSHA512ANDAES_256` + 随机盐 + 随机 IV）。
  - `com.frame.me.encrypt.cli.JasyptEncryptCli` — 离线生成 `ME(密文)` 的 `main` 工具。
  - `com.frame.me.encrypt.config.EncryptAutoConfiguration` — 配了主密码后暴露 `org.jasypt.encryption.StringEncryptor` Bean，供业务代码对自身数据加解密（与配置解密共用主密码与算法，密文互通）。
  - `com.frame.me.encrypt.EncryptConstant` — 配置键与默认值常量。
- **注册**：配置解密的 `EncryptablePropertyEnvironmentPostProcessor` 走 `META-INF/spring.factories` 的 `org.springframework.boot.EnvironmentPostProcessor` 键（EnvironmentPostProcessor 早于自动装配，**不能**用 `AutoConfiguration.imports`）；业务用的 `EncryptAutoConfiguration` 是普通自动装配，走 `AutoConfiguration.imports`。两者并存、互不影响。
- **启用条件**：读到主密码 `me.encrypt.password`（兼容环境变量 `ME_ENCRYPT_PASSWORD`、JVM 系统属性）时才解密；主密码缺失则跳过，对无密文应用零影响。
- **可配置项**（前缀 `me.encrypt`）：
  - `me.encrypt.password` — 主密码，运行时由环境变量 / 启动参数注入，**不落配置文件**。
  - `me.encrypt.algorithm` — PBE 算法名，默认 `PBEWITHHMACSHA512ANDAES_256`。
  - `me.encrypt.iterations` — 密钥迭代次数，默认 `100000`（可通过该配置覆盖；
    若修改默认值，需用 `JasyptEncryptCli` 重新生成已有的 `ME(...)` 密文）。
- **使用方式**：
  - 生成密文：`ME_ENCRYPT_PASSWORD=xxx java -cp ... com.frame.me.encrypt.cli.JasyptEncryptCli <明文>`（主密码**不接受命令行参数**——argv 会进 shell history 且 `ps` 可见；可用环境变量、`-Dme.encrypt.password` 或控制台交互输入）。
  - 配置：把敏感值写成 `password: ME(密文)`。
  - 运行：通过环境变量/启动参数注入主密码，**不写入配置文件**：`ME_ENCRYPT_PASSWORD=xxx` 或 `-Dme.encrypt.password=xxx`。
- **设计约定**：
  - 已纳入 `frame-me-boot`，业务 `xx-service` 引入 `frame-me-boot` 即获得能力。
  - **原位解密、不动优先级**：含密文的属性源在原位置被包装替换（jasypt-spring-boot 同款架构），不是把解密结果 `addFirst` 到最高优先级——加密配置项仍可被命令行 / 环境变量临时覆盖；密文损坏或主密码错误在启动期预解密时 fail-fast。
  - 跳过系统环境变量属性源（规避 Boot 3.5+ 系统环境源不被包装解密的已知行为，且密文放环境变量无意义）。
  - 本质是「用主密码加密其它密钥」，主密码仍需妥善保管；若要求密钥完全不落地，应改用 Vault/KMS 方案。

## `frame-me-starter-op-audit`

- **定位**：审计/行为日志 starter，通过注解无侵入地记录业务操作（动作、参数、返回值、异常、耗时），默认输出到日志，并可通过事件桥接将日志定向发送到专门的审计服务持久化。
- **依赖**：`frame-me-api`、`frame-me-starter-base`、`spring-boot-starter`、`spring-aop`、`aspectjweaver`、`fastjson2`、`lombok`。
- **关键类**：
  - `com.frame.me.op.audit.annotation.AuditLog` — 标记需要记录审计日志的方法。
  - `com.frame.me.op.audit.aspect.AuditLogAspect` — AOP 切面，拦截方法并组装 `AuditLogRecord`；操作人 SPI 异常降级为 `anonymous`，不阻断业务；`maxParamLength` 同时约束参数与返回值。
  - `com.frame.me.op.audit.core.AuditLogEvent` — 审计事件，继承 `MeApplicationEvent`。
  - `com.frame.me.op.audit.core.AuditLogRecord` — 审计记录负载。
  - `com.frame.me.op.audit.listener.AuditLogLogger` — 本地 `@EventListener`，默认输出结构化日志；仅打印本服务产生的事件（按事件源与当前服务名比对），不重复打印其他服务广播来的事件。
  - `com.frame.me.op.audit.spi.IAuditLogOperatorSupplier` — 操作人提供接口，默认返回 `anonymous`。
  - `com.frame.me.op.audit.config.AuditAutoConfiguration` — 自动装配入口。
  - `com.frame.me.op.audit.config.AuditProperties` — `me.audit` 配置属性绑定。
- **自动装配**：通过 `frame-me-starter-op-audit/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `AuditAutoConfiguration`。
- **用法、配置与注意事项**：见 [guides/audit.md](./guides/audit.md)（`@AuditLog` 用法与 `description` 占位符、`me.audit.*` 配置、桥接审计服务、Redis Pub/Sub 不持久化的限制）。
- **可配置项**（前缀 `me.audit`）：
  - `me.audit.enabled` — 是否启用审计模块，默认 `true`。
  - `me.audit.log-enabled` — 是否在本地打印审计日志，默认 `true`。
  - `me.audit.target-service` — 审计服务名；为空时通过事件桥接广播，配置为具体服务名时定向发送。
  - `me.audit.max-param-length` — 参数 JSON 最大长度，`0` 表示不限制，默认 `0`。
- **设计约定**：
  - 不硬依赖事件桥接：`me.event-bridge.enabled=false` 时模块仍正常装配，`AuditLogAspect` 降级为仅本地发布审计事件（`AuditLogLogger` 等本进程监听器照常消费，审计中心收不到）；`EventBridgeProperties` 由本模块兜底注册。

## `frame-me-starter-msg-notify`

- **定位**：消息通知 starter，提供统一的邮件 / Webhook / 短信多渠道通知能力。业务代码通过 `INotifySender` 接口发送通知，无需关心底层通道；具体通道由 `me.notify.*` 配置决定。
- **依赖**：`frame-me-api`、`frame-me-starter-base`、`spring-boot-starter-mail`、`fastjson2`；`freemarker` 为 optional 依赖（缺席时 `templateType=null`/`placeholder` 的模板回退为占位符 `${key}` 替换；显式指定 `freemarker`/`html` 类型但依赖缺席时打 WARN 并返回原文）。
- **关键类**：
  - `com.frame.me.base.notify.INotifySender` — 通用通知发送接口（定义在 `frame-me-starter-base`）。
  - `com.frame.me.notify.api.INotifyClient` — 单个通知客户端抽象。
  - `com.frame.me.notify.api.INotifyTemplateEngine` — 模板引擎抽象（支持 FreeMarker / 占位符）。
  - `com.frame.me.notify.notify.MsgNotifySender` — `INotifySender` 实现，支持全局默认、指定通道、指定命名客户端三种发送方式。
  - `com.frame.me.notify.util.NotifyClientFactory` / `com.frame.me.notify.util.NotifyUtils` — 客户端工厂与发送工具。
  - `com.frame.me.notify.email.EmailNotifyClient` — 邮件客户端实现。
  - `com.frame.me.notify.webhook.WebhookNotifyClient` — Webhook 客户端实现。
  - `com.frame.me.notify.sms.SmsNotifyClient` — 短信客户端实现。
  - `com.frame.me.notify.config.NotifyAutoConfiguration` — 自动装配入口。
  - `com.frame.me.notify.config.NotifyProperties` — `me.notify` 配置属性绑定。
  - `com.frame.me.notify.config.EmailChannelProperties` / `WebhookChannelProperties` / `SmsChannelProperties` — 各通道配置。
- **自动装配**：通过 `frame-me-starter-msg-notify/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `NotifyAutoConfiguration`。
- **启用条件**：
  - `me.notify.enabled=true`（默认 `true`，可显式关闭）。
  - `me.notify.sender.enabled=true`（默认 `true`，控制是否注册 `INotifySender` Bean）。
- **可配置项**（前缀 `me.notify`）：
  - `enabled` — 是否启用通知模块，默认 `true`。
  - `sender.enabled` — 是否注册 `INotifySender` Bean，默认 `true`。
  - `global-default` — 全局默认通道类型，如 `email` / `webhook` / `sms`；未配置时无全局默认发送能力。
  - `global-receivers` — 全局默认接收者列表；调用方未指定接收者时使用（例如异步异常通知）。
  - `include-error-detail` — 通知发送失败时，`NotifyResult.message` 是否包含异常原始信息（如 `e.getMessage()`、响应体），默认 `false`；关闭时返回统一通用文案，避免内部信息外泄。
  - `email.*` — 邮件通道配置（host、port、username、password、clients 等）。
  - `webhook.*` — Webhook 通道配置（url、headers、clients 等）。
  - `sms.*` — 短信通道配置（url、headers、clients 等）。
- **设计约定**：
  - 已纳入 `frame-me-boot`，业务 `xx-service` 引入 `frame-me-boot` 即可获得通知能力。
  - 发送方法支持 `List<String>` 与单个 `String` 接收者两种重载。
  - 传入接收者为空时，`MsgNotifySender` 会自动回退到 `me.notify.global-receivers`。
  - 通知能力未配置或参数为空（channel/clientName 为 null）时，发送方法返回 `false` 并记录 debug 日志，不抛异常。

**示例配置**：

```yaml
me:
  notify:
    global-default: email
    global-receivers:
      - ops@example.com
      - admin@example.com
    email:
      host: smtp.example.com
      port: 587
      username: alert@example.com
      password: ${EMAIL_PASSWORD}
      clients:
        marketing:
          host: smtp.marketing.example.com
          username: marketing@example.com
```

**示例代码**：

```java
@Service
@RequiredArgsConstructor
public class AlertService {

    private final INotifySender notifySender;

    public void alert(String title, String content) {
        // 使用全局默认通道和全局默认接收者
        notifySender.send(title, content, Collections.emptyList());

        // 指定单个接收者
        notifySender.send(title, content, "dev@example.com");

        // 指定通道
        notifySender.sendChannel("webhook", title, content, List.of("https://hooks.example.com/ops"));

        // 指定命名客户端
        notifySender.sendClient("email:marketing", title, content, List.of("user@example.com"));
    }
}
```

## `frame-me-boot`

- **定位**：聚合启动模块 / service 入口，本身不包含业务代码，用于把一组通用 starter 打包成一条依赖对外提供。
- **依赖**：`frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-multi-redis`、`frame-me-starter-l1l2-cache`、`frame-me-starter-sensi-encrypt`、`frame-me-starter-sse-mvc`、`frame-me-starter-op-audit`、`frame-me-starter-msg-notify`（通过传递依赖自动引入 `frame-me-starter-base` 与 `frame-me-api`）。
- **关键类**：
  - `com.frame.me.boot.BootConstant` — 占位常量类。
- **使用方**：业务工程的 `xx-service` 模块。
- **设计约定**：
  - 业务 `xx-service` 通过引入 `frame-me-boot` 一键启动通用能力。
  - `frame-me-boot` 无自己的自动装配类，依赖的 `frame-me-starter-base` 等模块会通过传递依赖自动注册。
  - `frame-me-adapter`（含 `frame-me-adapter-starter`）与 `frame-me-starter-doc-openapi` 不纳入聚合，因为不同项目通常会重写适配层或按需引入文档能力。
  - `frame-me-starter-auth-jwt`、`frame-me-starter-auth-sa-token`、`frame-me-starter-auth-rbac`、`frame-me-starter-ws-mvc`、`frame-me-starter-dynamic-ds`、`frame-me-starter-mybatis-plus` / `frame-me-starter-mybatis-flex` 同样按需引入，不纳入聚合。

```xml
<!-- 业务 xx-service：引入通用能力 -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-boot</artifactId>
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

- **扩展提示**：新增通用功能子模块后，应将其加入 `frame-me-boot` 的依赖列表；`frame-me-adapter-starter`、`frame-me-starter-doc-openapi` 等可替换/可选模块应保持独立，不加入 `frame-me-boot`。

## `frame-me-tester`

- **定位**：示例与验证聚合模块，本身不包含源码，仅聚合 `frame-me-tester-api` 与 `frame-me-tester-service`。
- **子模块**：
  - `frame-me-tester-api`：契约接口层。
  - `frame-me-tester-service`：实现层与可运行入口。

## `frame-me-tester-api`

- **定位**：示例业务契约模块，演示业务 `xx-api` 应如何组织。
- **依赖**：`frame-me-api`。
- **关键类**：
  - `com.frame.me.tester.api.IDemoApi` — 演示数据 API 契约（MyBatis-Plus 版），使用 Spring HTTP Interface（`@HttpExchange`、`@GetExchange`、`@PostExchange` 等）；服务端实现当前已注释（见 `frame-me-tester-service`）。
  - `com.frame.me.tester.api.IFlexDemoApi` — 演示数据 API 契约（MyBatis-Flex 版，当前服务端实际实现）。
  - `com.frame.me.tester.api.IHealthApi` — 健康检查 API 契约。
  - `com.frame.me.tester.api.IDataSourceApi` — 数据源切换与连接池信息查询契约。
  - `com.frame.me.tester.api.IRedisApi` — Redis 操作与 Redisson 分布式锁 API 契约。
  - `com.frame.me.tester.api.dto.DemoDTO` — 演示数据传输对象，含校验分组。
  - `com.frame.me.tester.api.dto.FlexDemoDTO` — 演示数据传输对象（Flex 版）。
  - `com.frame.me.tester.api.query.DemoQuery` — 演示分页查询参数。
  - `com.frame.me.tester.api.query.FlexDemoQuery` — 演示分页查询参数（Flex 版）。
  - `com.frame.me.tester.api.query.DemoComplexQuery` — 演示复杂查询参数（含 `@TimeRange`）。
  - `com.frame.me.tester.api.query.DemoOldQuery` — 演示老规范分页查询参数（使用 `PageParam`）。
  - `com.frame.me.tester.api.vo.DemoVO` — 演示返回视图对象。
  - `com.frame.me.tester.api.vo.FlexDemoVO` — 演示返回视图对象（Flex 版）。
  - `com.frame.me.tester.api.vo.DemoComplexVO` — 演示复杂查询返回视图对象。
  - `com.frame.me.tester.event.UserCreatedPayload` — 用户创建事件负载。
  - `com.frame.me.tester.event.UserCreatedEvent` — 用户创建事件（继承 `MeApplicationEvent`）。
  - `com.frame.me.tester.event.UserCreatedEventType` — 用户创建事件类型映射。
  - `com.frame.me.tester.event.UserCreatedEventConfiguration` — 事件配置类（暴露 `UserCreatedEventType` Bean）。
- **设计约定**：
  - API 契约推荐用 Spring HTTP Interface 声明，便于后续生成 HTTP 客户端。
  - DTO / Query / VO 放在 `xx-api` 中，供服务提供方与消费方共享。

## `frame-me-tester-service`

- **定位**：示例业务实现层与可运行 Spring Boot 入口。
- **依赖**：`frame-me-tester-api`、`frame-me-boot`、`frame-me-starter-auth-jwt`、`frame-me-starter-auth-rbac`、`frame-me-starter-mybatis-flex`、`frame-me-starter-ws-mvc`、`redisson`、`spring-boot-starter-test`（test scope）；`frame-me-adapter-starter`、`frame-me-starter-dynamic-ds`、`frame-me-starter-mybatis-plus`、`druid-spring-boot-4-starter` 在 POM 中注释保留，可按需恢复。
- **关键类/文件**：
  - `com.frame.me.tester.Application` — `@SpringBootApplication` 启动类。
  - `com.frame.me.tester.controller.HealthController` — 实现 `IHealthApi` 的健康检查端点（返回 `UP`），`@Anonymous` 匿名可访问。
  - `com.frame.me.tester.controller.FlexDemoController` — 实现 `IFlexDemoApi`，演示 MyBatis-Flex CRUD、分页、校验分组。
  - `com.frame.me.tester.controller.DataSourceController` — 实现 `IDataSourceApi`，演示多数据源切换与连接池信息查询；**当前整体注释保留**（未装配），如需启用须先加字段白名单脱敏（jdbc-url/密码不可外泄）。
  - `com.frame.me.tester.controller.RedisController` — 实现 `IRedisApi`，演示 Redis 操作与 Redisson 分布式锁。
  - `com.frame.me.tester.auth.DemoAuthUserDetailsService` — `IAuthUserDetailsService` 演示实现，接入 JWT 登录（`PasswordUtils` BCrypt 校验）；硬编码 `admin/123456` 示例账号与 `application.yml` 中硬编码 JWT secret 仅供演示，真实业务必须改为数据库查询与独立密钥（starter 层 `JwtTokenService` 启动校验保证密钥非空）。
  - `com.frame.me.tester.service.IFlexDemoService` / `com.frame.me.tester.service.impl.FlexDemoServiceImpl` — 演示 Service 层（Flex 版）；`update` 校验 version 非空，避免 MyBatis-Flex 在 version 为 null 时静默跳过乐观锁。
  - `com.frame.me.tester.service.convert.FlexDemoConvert` — MapStruct 转换器（`@Mapper(componentModel = "spring")`）。
  - `com.frame.me.tester.entity.FlexDemoEntity` — 演示实体，继承 MyBatis-Flex `BaseVersionEntity`。
  - `com.frame.me.tester.mapper.FlexDemoMapper` — 演示 Mapper，继承 MyBatis-Flex `BaseMapper<FlexDemoEntity>`。
  - `com.frame.me.tester.infrastructure.config.DataSourceWarmer` — 数据源预热配置。
  - MyBatis-Plus 版演示类 `DemoController` / `DemoEntity` / `DemoMapper` / `IDemoService` / `DemoServiceImpl` / `DemoConvert` **整体注释保留**，作为切回 MyBatis-Plus 时的参考。
  - `com.frame.me.tester.cache.DemoServiceCacheTest` — 演示 JetCache 两级缓存集成测试。
  - `com.frame.me.tester.encrypt.JasyptEncryptTest` — 演示 Jasypt 配置加密解密测试。
  - `com.frame.me.tester.event.UserCreatedEventFlowTest` — 演示事件桥接端到端测试。
  - `com.frame.me.tester.redis.RedissonLockTest` — 演示 Redisson 分布式锁集成测试。
  - **测试已知问题**（待后续优化）：
    - `RedissonLockTest`/`UserCreatedEventFlowTest` 用 `Assumptions.assumeTrue` 跳过 Docker 不可用场景，但静态 `@Container` 可能在 `BeforeAll` 阶段先于 `assumeTrue` 启动并报错；建议改用 `@EnabledIf("isDockerAvailable")` 类级守卫。
    - `RedissonLockTest.shouldExpireAfterLeaseTime` 名为"租期到期"实为"主动释放后重获取"，名不副实。
    - `RedissonLockTest.shouldOnlyAllowOneThreadInCriticalSection` 的 `successCount >= 1` 断言过弱（无法有效验证互斥）。
    - `DemoServiceCacheTest` 整体 `@Disabled`（依赖被注释的 `DemoServiceImpl`），当前无实际覆盖。
  - `com.frame.me.tester.ApplicationTests` — 上下文加载测试。
  - `com.frame.me.tester.AbstractIntegrationTest` — Testcontainers + MySQL 集成测试基类。
  - 测试目录按能力划分：`async` / `auth` / `cache` / `encrypt` / `event` / `flex` / `mybatis` / `redis`。
  - `frame-me-tester/frame-me-tester-service/src/main/resources/application.yml` — 端口 `9090`（管理端口 `9091`），应用名 `frame-me-tester`；MyBatis-Flex 双数据源（`mybatis-flex.datasource.master` = `frame_me_test`，`second` = `frame_me_test_2`）；`me.auth.jwt`（接口路径 `/base/auth`）+ `me.auth.whitelist`；`me.sse.path=/base/sse`；`me.redis.clients.second`；JetCache（kryo5 序列化）；OpenAPI 分组（`tester-api` 匹配 `/api/**`、`base-api` 匹配 `/base/**`）；P6Spy 配置。
- **构建插件**：包含 `spring-boot-maven-plugin`，用于打包可运行 Jar。
- **扩展提示**：作为集成验证入口，新模块加入后应在此添加对应的集成测试或示例 Controller。

## IDE 配置提示

所有带 `@ConfigurationProperties` 的 starter 模块（`frame-me-starter-base`、`frame-me-starter-mybatis-plus`、`frame-me-starter-mybatis-flex`、`frame-me-starter-multi-redis`、`frame-me-starter-sse-mvc`、`frame-me-starter-ws-mvc`、`frame-me-starter-doc-openapi`、`frame-me-starter-sensi-encrypt`、`frame-me-starter-op-audit`、`frame-me-starter-msg-notify`）在编译时都会生成 `META-INF/spring-configuration-metadata.json`。在 IntelliJ IDEA（Ultimate / Community 均支持）或 VS Code（安装 Spring Boot Extension Pack）中编辑 `application.yml` / `application.properties` 时，输入 `me.` / `spring.data.redis.redisson.` 等前缀即可获得属性名、类型、默认值和中文描述提示。

生成元数据依赖 `spring-boot-configuration-processor` 注解处理器，已统一配置在根 POM 的 `annotationProcessorPaths` 中，各 starter 模块无需额外依赖即可生效。注解处理器分层声明：根 POM 全局挂 `lombok` 与 `spring-boot-configuration-processor`（通用基础设施）；`mybatis-flex-processor`（生成 `TableDef`）与 `mapstruct-processor`（生成 `*ConvertImpl`）仅 `frame-me-tester-service` 使用，下放到该模块 pom 自行声明，其余模块不再加载无用处理器。

## 模块依赖速查表

| 模块 | 直接依赖 |
|---|---|
| `frame-me-api` | 无内部依赖 |
| `frame-me-starter-base` | `frame-me-api` |
| `frame-me-starter-mybatis-plus` | `frame-me-api` |
| `frame-me-starter-mybatis-flex` | `frame-me-api`、`frame-me-starter-base` |
| `frame-me-adapter-api` | `frame-me-api` |
| `frame-me-adapter-starter` | `frame-me-adapter-api`、`frame-me-starter-base` |
| `frame-me-starter-dynamic-ds` | `frame-me-starter-base` |
| `frame-me-starter-doc-openapi` | `spring-boot-autoconfigure`（框架依赖） |
| `frame-me-starter-auth` | `frame-me-starter-base` |
| `frame-me-starter-auth-rbac` | `frame-me-starter-auth`、`caffeine`（`frame-me-starter-multi-redis` optional） |
| `frame-me-starter-auth-jwt` | `frame-me-starter-auth`、`jjwt-api`/`jjwt-impl`/`jjwt-gson`、`spring-security-crypto`（`frame-me-starter-multi-redis` optional） |
| `frame-me-starter-auth-sa-token` | `frame-me-starter-auth`、`sa-token-spring-boot4-starter`、`fastjson2`、`spring-security-crypto`（`frame-me-starter-multi-redis` optional） |
| `frame-me-starter-cloud` | `frame-me-starter-base` |
| `frame-me-starter-sse-mvc` | `frame-me-api` |
| `frame-me-starter-ws-mvc` | `frame-me-api` |
| `frame-me-starter-op-audit` | `frame-me-api`、`frame-me-starter-base` |
| `frame-me-starter-msg-notify` | `frame-me-api`、`frame-me-starter-base` |
| `frame-me-boot` | `frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-multi-redis`、`frame-me-starter-l1l2-cache`、`frame-me-starter-sensi-encrypt`、`frame-me-starter-sse-mvc`、`frame-me-starter-op-audit`、`frame-me-starter-msg-notify` |
| `frame-me-tester-api` | `frame-me-api` |
| `frame-me-tester-service` | `frame-me-tester-api`、`frame-me-boot`、`frame-me-starter-auth-jwt`、`frame-me-starter-auth-rbac`、`frame-me-starter-mybatis-flex`、`frame-me-starter-ws-mvc` |
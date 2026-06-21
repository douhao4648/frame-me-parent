# 模块速查

## `frame-me-api`

- **定位**：纯接口 / Interfacer 契约模块，禁止引入 Spring 依赖。
- **依赖**：无。
- **关键类**：
  - `com.frame.me.api.result.IResult<T>` — 统一响应结果接口。
  - `com.frame.me.api.ApiConstant` — 占位常量接口。
- **使用方**：业务工程的 `xx-api` 模块。
- **设计约定**：
  - 业务 `xx-api` 通过引入 `frame-me-api` 获得统一的接口契约。
  - 业务 `xx-api` 之间可以相互引用，用于跨业务接口调用。
  - `frame-me-api` 不实现任何具体能力，只定义最基础的跨模块接口与常量。

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
  - `com.frame.me.base.mybatis.mapper.FrameBaseMapper` — 项目基础 Mapper。
  - `com.frame.me.base.mybatis.service.FrameBaseService` — 项目基础 Service 接口。
  - `com.frame.me.base.mybatis.service.impl.FrameBaseServiceImpl` — 项目基础 Service 实现。
  - `com.frame.me.base.mybatis.plugin.BaseMetaObjectHandler` — 公共字段自动填充，需通过 `frame.me.mybatis.meta-object-handler.enabled=true` 开启。
  - `com.frame.me.base.mybatis.plugin.IdGeneratorInfoPrinter` — 启动时打印当前 Snowflake 的 workerId / datacenterId。
  - `com.frame.me.base.mybatis.config.MybatisPlusProperties` — `frame.me.mybatis` 配置属性绑定。
  - `com.frame.me.base.mybatis.config.MybatisPlusConfiguration` — 分页插件、乐观锁插件、公共字段自动填充处理器以及自定义 ID 生成器注册。
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
  - **Mapper 接口必须标注 `@Mapper` 注解**，以便 MyBatis-Plus starter 自动扫描。
- **扩展提示**：与 Spring Web 相关的基础能力（拦截器、参数解析器、统一日志等）适合放在这里。

## `frame-me-starter-adapter`

- **定位**：内部 `IResult<T>` 与外部 `Response<T>` 的适配层。
- **依赖**：`frame-me-starter-base`、`lombok`。
- **关键类**：
  - `com.frame.me.adapter.advice.Result2ResponseAdvice` — `ResponseBodyAdvice`，将 `IResult<T>` 转为 `Response<T>`。
  - `com.frame.me.adapter.result.Response<T>` — 外部响应结构。
  - `com.frame.me.adapter.config.AdapterAutoConfiguration` — 自动装配入口。
  - `com.frame.me.adapter.AdapterConstant` — 占位常量接口。
- **自动装配**：通过 `frame-me-starter-adapter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `AdapterAutoConfiguration`。
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

## `frame-me-booter`

- **定位**：聚合启动模块 / service 入口，本身不包含业务代码，用于把一组通用 starter 打包成一条依赖对外提供。
- **依赖**：`frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-dynamic-ds`（通过传递依赖自动引入 `frame-me-starter-base` 与 `frame-me-api`）。
- **关键类**：
  - `com.frame.me.booter.BooterConstant` — 占位常量接口。
- **使用方**：业务工程的 `xx-service` 模块。
- **设计约定**：
  - 业务 `xx-service` 通过引入 `frame-me-booter` 一键启动通用能力。
  - `frame-me-booter` 无自己的自动装配类，依赖的 `frame-me-starter-base` 等模块会通过传递依赖自动注册。
  - `frame-me-starter-adapter` 与 `frame-me-starter-doc-openapi` 不纳入聚合，因为不同项目通常会重写适配层或按需引入文档能力。

```xml
<!-- 业务 xx-service：引入通用能力 -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-booter</artifactId>
</dependency>

<!-- 默认适配层（可选，可被项目自定义适配层替换） -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-starter-adapter</artifactId>
</dependency>

<!-- 接口文档（可选） -->
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-starter-doc-openapi</artifactId>
</dependency>
```

- **扩展提示**：新增通用功能子模块后，应将其加入 `frame-me-booter` 的依赖列表；`frame-me-starter-adapter`、`frame-me-starter-doc-openapi` 等可替换/可选模块应保持独立，不加入 `frame-me-booter`。

## `frame-me-tester`

- **定位**：Spring Boot 可运行入口与验证模块。
- **依赖**：`frame-me-booter`、`frame-me-starter-adapter`、`spring-boot-starter-test`（test scope）。
- **关键类/文件**：
  - `com.frame.me.tester.Application` — `@SpringBootApplication` 启动类。
  - `com.frame.me.tester.controller.HealthController` — 示例接口 `/health`，故意触发 NPE 以验证异常处理。
  - `com.frame.me.tester.controller.DemoController` — 示例接口 `/demo`，演示 MyBatis-Plus CRUD。
  - `com.frame.me.tester.entity.DemoEntity` — 演示实体，继承 `BaseEntity`。
  - `com.frame.me.tester.mapper.DemoMapper` — 演示 Mapper，继承 `FrameBaseMapper`。
  - `com.frame.me.tester.ApplicationTests` — 上下文加载测试。
  - `com.frame.me.tester.AbstractIntegrationTest` — Testcontainers + MySQL 集成测试基类。
  - `frame-me-tester/src/main/resources/application.yml` — 端口 `8080`，应用名 `frame-me-tester`，单/多数据源、MyBatis-Plus、OpenAPI、P6Spy 配置。
- **构建插件**：包含 `spring-boot-maven-plugin`，用于打包可运行 Jar。
- **扩展提示**：作为集成验证入口，新模块加入后应在此添加对应的集成测试或示例 Controller。

## 模块依赖速查表

| 模块 | 直接依赖 |
|---|---|
| `frame-me-api` | 无内部依赖 |
| `frame-me-starter-base` | `frame-me-api` |
| `frame-me-starter-adapter` | `frame-me-starter-base` |
| `frame-me-starter-dynamic-ds` | `frame-me-starter-base` |
| `frame-me-starter-doc-openapi` | 无内部框架依赖（仅 `springdoc-openapi-starter-webmvc-ui`） |
| `frame-me-starter-auth` | `frame-me-starter-base` |
| `frame-me-starter-cloud` | `frame-me-starter-base` |
| `frame-me-booter` | `frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-dynamic-ds` |
| `frame-me-tester` | `frame-me-booter`、`frame-me-starter-adapter` |
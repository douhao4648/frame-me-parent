# 架构设计

## 模块依赖图

```
                         frame-me-starter-doc-openapi
                                    ↓ 可选
frame-me-api  →  frame-me-starter-base  →  frame-me-starter-adapter   (可被外部项目重写)
                         ↓
                   frame-me-starter-auth    ───────────────────────┐
                         ↓                                  │
                   frame-me-starter-cloud  ────────────────────┘   │
                         ↓                            │   │
           frame-me-starter-dynamic-ds ────────────────┘   │
                         ↓                                 │
                      frame-me-booter ------------------------┘
                      （聚合启动模块，供外部 xx-service 引用，不含 adapter / openapi）
                         ↓
              frame-me-tester-api  ←  契约接口（@HttpExchange）
                         ↓
              frame-me-tester-service  ←  可运行入口
```

更准确的依赖关系：

- `frame-me-api`：最底层，无 Spring 依赖，纯接口/Interfacer 契约。
- `frame-me-starter-base`：依赖 `frame-me-api` + `spring-boot-starter-web`，提供 Spring Web 基础设施与 MyBatis-Plus 数据访问能力。
- `frame-me-starter-adapter`：依赖 `frame-me-starter-base`，默认适配层，可被外部项目重写。
- `frame-me-starter-dynamic-ds`：依赖 `frame-me-starter-base` + baomidou `dynamic-datasource-spring-boot4-starter`，多数据源能力。
- `frame-me-starter-doc-openapi`：依赖 `springdoc-openapi-starter-webmvc-ui`，接口文档能力，不依赖框架内部模块。
- `frame-me-starter-auth`：依赖 `frame-me-starter-base`，认证授权占位模块。
- `frame-me-starter-cloud`：依赖 `frame-me-starter-base`，微服务云组件占位模块。
- `frame-me-booter`：聚合启动模块，依赖 `frame-me-starter-auth`、`frame-me-starter-cloud`、`frame-me-starter-dynamic-ds`，本身无业务源码与自动装配，供外部业务工程的 `xx-service` 统一引入通用能力。`frame-me-starter-adapter` 与 `frame-me-starter-doc-openapi` 不纳入聚合。
- `frame-me-tester`：`pom` 聚合模块，包含 `frame-me-tester-api`（契约接口）与 `frame-me-tester-service`（可运行入口）。`frame-me-tester-api` 依赖 `frame-me-api`；`frame-me-tester-service` 依赖 `frame-me-tester-api`、`frame-me-booter`、`frame-me-starter-adapter`。通过 `frame-me-starter-base` 的 Maven profile 可选引入 `frame-me-starter-doc-openapi` 与 `p6spy`。

### interfacer / booter 使用约定

在基于本脚手架构建新业务工程时，推荐按以下方式引用：

- **业务 `xx-api` 模块** → 引用 `frame-me-api`
  - 只引入接口契约（如 `IResult<T>`、`ApiConstant`），不引入 Spring starter。
  - 业务 `xx-api` 之间可以相互引用，用于跨业务接口调用。
  - 推荐用 Spring HTTP Interface（`@HttpExchange`、`@GetExchange` 等）声明 API 契约，如 `frame-me-tester-api`。

- **业务 `xx-service` 模块** → 引用 `frame-me-booter`
  - 通过 `frame-me-booter` 一键拉起通用 starter 能力（auth、cloud、base 等）。
  - 若需要默认适配层，再额外引入 `frame-me-starter-adapter`，或项目自行实现。

`frame-me-tester` 是本脚手架的示例：`frame-me-tester-api` 承担契约层，`frame-me-tester-service` 作为实现层与可运行入口。

## 分层原则

1. **单向依赖**：下层模块不能依赖上层模块。`frame-me-api` 严禁引入 Spring 相关依赖。
2. **基础设施下沉**：通用异常、响应包装、全局异常处理、MyBatis-Plus 数据访问等基础设施放在 `frame-me-starter-base`；跨模块共享的接口与常量放在 `frame-me-api`。
3. **适配层独立**：与外部交互的格式转换（如 `IResult<T>` → `Response<T>`）放在 `frame-me-starter-adapter`。
4. **占位模块可扩展**：`frame-me-starter-auth` 与 `frame-me-starter-cloud` 当前仅含常量接口，用于未来承载认证、微服务云组件。

## Spring Boot 自动装配

项目使用 Spring Boot 3 风格的自动装配，不使用 `spring.factories`。

### 注册方式

每个提供 Bean 的模块在以下路径注册自动配置类：

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 现有配置

- `frame-me-starter-base` 注册 `com.frame.me.base.config.BaseAutoConfiguration`
  - 文件路径：`frame-me-starter-base/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 注册 Bean：`GlobalExceptionHandler`、`EnvironmentHelper`
- `frame-me-starter-base` 注册 `com.frame.me.base.mybatis.config.MybatisPlusConfiguration`
  - 注册 Bean：`MybatisPlusInterceptor`（含分页插件、乐观锁插件）、`BaseMetaObjectHandler`（需配置开启）、自定义 `IdentifierGenerator`（可选 worker-id 配置）。
- `frame-me-starter-adapter` 注册 `com.frame.me.adapter.config.AdapterAutoConfiguration`
  - 文件路径：`frame-me-starter-adapter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 注册 Bean：`Result2ResponseAdvice`
- `frame-me-starter-dynamic-ds` 注册 `com.frame.me.dynamic.ds.config.DynamicDataSourceAutoConfiguration`
  - 文件路径：`frame-me-starter-dynamic-ds/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 注册 Bean：`MeDynamicDataSourceProvider`（根据 `spring.datasource.*` 创建默认 `master` 数据源）。
- `frame-me-starter-doc-openapi` 注册 `com.frame.me.doc.openapi.config.DocOpenApiAutoConfiguration`
  - 文件路径：`frame-me-starter-doc-openapi/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 注册 Bean：`OpenAPI`、`GroupedOpenApi`（分组）。

`frame-me-booter` 作为聚合模块没有自己的自动装配类，它通过传递依赖自动引入 `frame-me-starter-base`、`frame-me-starter-dynamic-ds` 等模块的自动配置。`frame-me-starter-adapter` 与 `frame-me-starter-doc-openapi` 不纳入 `frame-me-booter`，因此外部项目可选择使用默认适配层/文档能力或自行实现。

### 配置类约定

```java
@Configuration(proxyBeanMethods = false)
public class XxxAutoConfiguration {

    @Bean
    public XxxService xxxService() {
        return new XxxService();
    }
}
```

要点：

- 使用 `@Configuration(proxyBeanMethods = false)` 避免 CGLIB 代理，提升启动性能。
- 一个配置类集中注册该模块对外暴露的 Bean。
- 当前未使用 `@Conditional*` 注解，表示无条件注册。

## 响应与异常流水线

### 正常请求

```
Controller 返回 IResult<T>
         ↓
Result2ResponseAdvice.beforeBodyWrite()
         ↓
序列化为 Response<T> JSON
```

### 异常请求

```
Controller 或 Service 抛出异常
         ↓
GlobalExceptionHandler 捕获并返回 IResult<T>
         ↓
Result2ResponseAdvice.beforeBodyWrite()
         ↓
序列化为 Response<T> JSON
```

### 字段映射

内部 `IResult<T>` 字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `code` | `Integer` | 状态码 |
| `msg` | `String` | 提示信息 |
| `data` | `T` | 业务数据 |
| `err` | `String` | 错误堆栈或详细错误 |
| `rid` | `String` | 请求 ID（当前未填充） |

外部 `Response<T>` 字段：

| 字段 | 类型 | 来源 |
|---|---|---|
| `code` | `Integer` | `IResult.code` |
| `message` | `String` | `IResult.msg` |
| `result` | `T` | `IResult.data` |
| `requestId` | `String` | 当前未填充（来自 `IResult.rid`） |

### 关键类路径

- `IResult<T>`：`frame-me-api/src/main/java/com/frame/me/api/result/IResult.java`
- `PageResult<T>`：`frame-me-api/src/main/java/com/frame/me/api/result/PageResult.java`
- `PageQuery`：`frame-me-api/src/main/java/com/frame/me/api/query/PageQuery.java`
- `Result<T>`：`frame-me-starter-base/src/main/java/com/frame/me/base/result/Result.java`
- `ResultCode`：`frame-me-starter-base/src/main/java/com/frame/me/base/result/ResultCode.java`
- `Response<T>`：`frame-me-starter-adapter/src/main/java/com/frame/me/adapter/result/Response.java`
- `Result2ResponseAdvice`：`frame-me-starter-adapter/src/main/java/com/frame/me/adapter/advice/Result2ResponseAdvice.java`
- `GlobalExceptionHandler`：`frame-me-starter-base/src/main/java/com/frame/me/base/advice/GlobalExceptionHandler.java`
- `BaseEntity`：`frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/entity/BaseEntity.java`
- `BaseVersionEntity`：`frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/entity/BaseVersionEntity.java`
- `BaseMetaObjectHandler`：`frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/plugin/BaseMetaObjectHandler.java`
- `PageUtils`：`frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/util/PageUtils.java`
- `SnowflakeUtils`：`frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/util/SnowflakeUtils.java`
- `IHealthApi`：`frame-me-tester/frame-me-tester-api/src/main/java/com/frame/me/tester/api/IHealthApi.java`
- `HealthController`：`frame-me-tester/frame-me-tester-service/src/main/java/com/frame/me/tester/controller/HealthController.java`

## 扩展提示

- `IResult.rid` 与 `Response.requestId` 字段已预留，但尚未实现请求 ID 生成与传递。
- `RetryException` 已定义，但 `GlobalExceptionHandler` 未对其单独处理，当前会落入通用 `Exception` 处理器。
- `frame-me-starter-auth` 与 `frame-me-starter-cloud` 为空壳模块，适合作为未来 JWT、Spring Security、Nacos、Gateway 等功能的载体。
- `HealthController` 故意触发 NPE，用于验证异常处理链路是否正常工作。

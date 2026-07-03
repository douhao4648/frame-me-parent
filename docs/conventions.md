# 编码约定

## 响应包装 `Result<T>`

类路径：`frame-me-starter-base/src/main/java/com/frame/me/base/result/Result.java`

`Result<T>` 是内部统一响应包装，所有 Controller / Service 层建议使用它作为返回值。

### 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `Integer` | 状态码 |
| `msg` | `String` | 提示信息 |
| `data` | `T` | 业务数据 |
| `err` | `String` | 错误详情或堆栈 |
| `rid` | `String` | 请求 ID（暂未使用） |

### 工厂方法

| 方法 | 用途 |
|---|---|
| `Result.success(T data)` | 成功，带数据 |
| `Result.success()` | 成功，无数据 |
| `Result.error(String message, Object... args)` | 系统错误，支持 Hutool 格式化 |
| `Result.error(Integer code, String message, String err)` | 自定义错误码与错误详情 |
| `Result.error(Integer code, String message, Throwable err)` | 自定义错误码与异常对象 |
| `Result.error(ResultCode resultCode, Object... args)` | 使用枚举状态码 |
| `Result.error(ResultCode resultCode, String message, Object... args)` | 使用枚举状态码并自定义消息 |
| `Result.of(ResultCode resultCode, T data)` | 通用构造 |

示例：

```java
return Result.success(user);
return Result.error(ResultCode.BAD_REQUEST, "参数 {} 不合法", param);
return Result.error("系统错误：{}", e.getMessage());
```

## 状态码枚举 `ResultCode`

类路径：`frame-me-starter-base/src/main/java/com/frame/me/base/result/ResultCode.java`

| 枚举 | 状态码 | 默认消息 |
|---|---|---|
| `SUCCESS` | 200 | 请求成功 |
| `BAD_REQUEST` | 400 | 参数错误 |
| `UNAUTHORIZED` | 401 | 未授权 |
| `FORBIDDEN` | 403 | 禁止访问 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `METHOD_NOT_ALLOWED` | 405 | 请求方法不支持 |
| `REQUEST_TIMEOUT` | 408 | 请求超时 |
| `CONFLICT` | 409 | 资源冲突 |
| `TOO_MANY_REQUESTS` | 429 | 请求过于频繁 |
| `ERROR` | 500 | 系统错误 |
| `SERVICE_UNAVAILABLE` | 503 | 服务不可用 |
| `BUSINESS_ERROR` | 600 | 业务异常 |

## 外部响应 `Response<T>`

类路径：`frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/result/Response.java`

`Response<T>` 是最终序列化给客户端的 JSON 形状，由 `Result2ResponseAdvice` 自动转换。

| 字段 | 类型 | 来源 |
|---|---|---|
| `code` | `Integer` | `Result.code` |
| `message` | `String` | `Result.msg` |
| `result` | `T` | `Result.data` |
| `requestId` | `String` | `Result.rid`（当前为 `null`） |

`Response<T>` 实现了 `Serializable`。

## 异常体系

所有异常均位于 `frame-me-starter-base/src/main/java/com/frame/me/base/exception/`。

### `BusinessException`

- 语义：业务规则不满足、可预期的业务错误。
- 默认状态码：`ResultCode.ERROR.getCode()`（500）。
- 日志级别：在 `GlobalExceptionHandler` 中使用 `error` 记录。
- HTTP 状态：默认 200（由 `Result` 的 `code` 字段表达业务错误）。

### `InternalException`

- 语义：系统内部错误、不可恢复的服务端异常。
- 日志级别：在 `GlobalExceptionHandler` 中使用 `error` 记录。
- HTTP 状态：`@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)`，即 HTTP 500。

### `RetryException`

- 语义：可重试的临时失败（当前未单独处理，会落入通用 `Exception` 处理器）。

### 构造器签名

三种异常类构造器完全一致，均支持 Hutool 的 `StrUtil.format`：

```java
public BusinessException(String message)
public BusinessException(String message, Object... args)
public BusinessException(Integer code, String message)
public BusinessException(ResultCode resultCode)
public BusinessException(ResultCode resultCode, String message, Object... args)
```

示例：

```java
throw new BusinessException(ResultCode.BAD_REQUEST, "用户名 {} 已存在", username);
throw new InternalException("数据库连接失败");
```

## 全局异常处理 `GlobalExceptionHandler`

类路径：`frame-me-starter-base/src/main/java/com/frame/me/base/advice/GlobalExceptionHandler.java`

| 处理器 | 捕获异常 | HTTP 状态 | 日志级别 | 返回值 |
|---|---|---|---|---|
| `handleBusinessException` | `BusinessException` | 默认 200 | `error` | `Result.error(code, message, exception)` |
| `handleInternalException` | `InternalException` | 500 | `error` | `Result.error(code, message, exception)` |
| `handleMethodArgumentNotValidException` | `MethodArgumentNotValidException`（`@RequestBody` 校验失败） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, 首条字段错误消息)` |
| `handleConstraintViolationException` | `ConstraintViolationException`（`@PathVariable`/`@RequestParam` 校验失败） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, 首条约束错误消息)` |
| `handleBindException` | `BindException`（表单/查询参数绑定失败） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, 首条字段错误消息)` |
| `handleHttpMessageNotReadableException` | `HttpMessageNotReadableException`（请求体缺失或不可读） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, "请求体不能为空")` |
| `handleException` | `Exception` | 默认 200 | `error` | `Result.error(ResultCode.ERROR, message, exception)` |

## 编码风格

- **包名**：`com.frame.me.<module>`，与 Maven 模块后缀一致。
- **Lombok**：使用 `@Data`、`@Getter`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@Slf4j`。
- **常量容器**：每个模块定义一个空的 `*Constant` 接口作为占位，如 `CommonConstant`、`BaseConstant`、`AuthConstant`。
- **注释**：类级 Javadoc 使用中文。
- **类设计**：当前没有类被声明为 `final`，保持默认可继承。

## 参数校验约定

项目使用 Jakarta Validation（`jakarta.validation`），结合 Spring 的 `@Validated` / `@Valid` 进行参数校验。

### 校验分组 `CreateGroup` / `UpdateGroup`

类路径：

- `frame-me-api/src/main/java/com/frame/me/validation/CreateGroup.java`
- `frame-me-api/src/main/java/com/frame/me/validation/UpdateGroup.java`

用于区分新增与更新场景下的校验规则，例如：

```java
public class DemoDTO {

    @NotNull(groups = UpdateGroup.class, message = "更新时 ID 不能为空")
    private Long id;

    @NotBlank(groups = CreateGroup.class, message = "新增时名称不能为空")
    private String name;
}
```

Controller 中按场景指定分组：

```java
@PostExchange
IResult<Long> create(@Validated(CreateGroup.class) @RequestBody DemoDTO dto);

@PutExchange("/{id}")
IResult<Boolean> update(@Validated(UpdateGroup.class) @RequestBody DemoDTO dto);
```

### 时间范围校验 `@TimeRange`

类路径：

- 注解：`frame-me-api/src/main/java/com/frame/me/validation/annotation/TimeRange.java`
- 校验器：`frame-me-api/src/main/java/com/frame/me/validation/validator/TimeRangeValidator.java`

类级校验注解，用于校验对象中两个时间字段满足 `开始时间 <= 结束时间`。任一字段为空时不校验。

| 属性 | 说明 | 默认值 |
|---|---|---|
| `message` | 校验失败消息 | `开始时间不能晚于结束时间` |
| `startField` | 开始时间字段名 | `startTime` |
| `endField` | 结束时间字段名 | `endTime` |

示例：

```java
@TimeRange(startField = "startTime", endField = "endTime")
public class DemoComplexQuery {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
```

## 数据访问层约定

### 基础实体 `BaseEntity`

类路径：`frame-me-starter-mybatis-plus/src/main/java/com/frame/me/mybatis/plus/entity/BaseEntity.java`

所有业务实体建议继承 `BaseEntity`，已内置以下公共字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 主键，雪花算法 |
| `createTime` | `LocalDateTime` | 创建时间，自动填充 |
| `updateTime` | `LocalDateTime` | 更新时间，自动填充 |
| `deleted` | `Integer` | 逻辑删除标志，0 未删除 / 1 已删除 |

### 基础实体 `BaseVersionEntity`

类路径：`frame-me-starter-mybatis-plus/src/main/java/com/frame/me/mybatis/plus/entity/BaseVersionEntity.java`

继承 `BaseEntity`，额外提供乐观锁版本号：

| 字段 | 类型 | 说明 |
|---|---|---|
| `version` | `Integer` | 乐观锁版本号 |

### Mapper 基类 `BaseMapper<T>`

业务 Mapper 直接继承 MyBatis-Plus 的 `com.baomidou.mybatisplus.core.mapper.BaseMapper<T>`，即可获得通用 CRUD 能力。项目当前未提供额外的 `FrameBaseMapper` 封装层。

### Mapper 扫描

业务 Mapper 接口必须标注 `@Mapper` 注解：

```java
@Mapper
public interface FmsDeviceMapper extends BaseMapper<FmsDevice> {
}
```

MyBatis-Plus starter 会自动扫描启动类所在包及其子包下的 `@Mapper` 接口。

### 公共字段自动填充 `BaseMetaObjectHandler`

类路径：`frame-me-starter-mybatis-plus/src/main/java/com/frame/me/mybatis/plus/plugin/BaseMetaObjectHandler.java`

开启方式：`me.mybatis.meta-object-handler.enabled=true`

自动填充行为：

| 操作 | 填充字段 | 值 |
|---|---|---|
| `insert` | `createTime` | 当前时间 |
| `insert` | `updateTime` | 当前时间 |
| `insert` | `deleted` | `0` |
| `insert` | `version` | `1`（仅 `BaseVersionEntity` 子类） |
| `update` | `updateTime` | 当前时间 |

### 分页工具

项目有两套分页规范，对应两个工具类：

**新规范 `PageUtils`**（默认）

类路径：`frame-me-starter-mybatis-plus/src/main/java/com/frame/me/mybatis/plus/util/PageUtils.java`

在 `com.frame.me.api.query.PageQuery` / `com.frame.me.api.result.PageData` 与 MyBatis-Plus `Page` 之间转换。例如：

```java
Page<DemoEntity> page = demoMapper.selectPage(PageUtils.toPage(query), wrapper);
PageData<DemoVO> result = PageUtils.toPageData(page, DemoConvert.INSTANCE::toVo);
```

**老规范 `PageableUtils`**（兼容老接口规范）

类路径：`frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/mybatis/util/PageableUtils.java`

在 `com.frame.me.adapter.api.query.PageParam` / `com.frame.me.adapter.api.result.PageResult` 与 MyBatis-Plus `Page` 之间转换。**凡集成 `frame-me-adapter-starter` 的项目即遵循老规范**，此时使用本工具：

```java
Page<DemoEntity> page = demoMapper.selectPage(PageableUtils.toPage(param), wrapper);
PageResult<DemoVO> result = PageableUtils.toPageResult(page, DemoConvert.INSTANCE::toVo);
```

### Service 层

项目当前未提供统一的 Service 基类封装。业务 Service 接口可直接继承 MyBatis-Plus 的 `com.baomidou.mybatisplus.extension.service.IService<T>`，实现类继承 `com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<M, T>`，或根据业务自行约定。

### 表名到实体名映射

数据库表名如 `spo_fms_device`，生成实体类时去掉第一个下划线前缀：

```
spo_fms_device → FmsDevice
```

实体类通过 `@TableName("spo_fms_device")` 显式声明对应表名。

### 单数据源配置

在 `application.yml` 中配置单数据源（HikariCP）：

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
```

### MyBatis-Plus 配置

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      id-type: assign_id
```

### 多数据源配置

引入 `frame-me-starter-dynamic-ds` 后，可在 `application.yml` 中同时配置单数据源（作为默认 `master`）和 dynamic-datasource 扩展数据源：

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

启用条件：

- `me.dynamic-datasource.enabled=true`（默认 `true`，可省略）。
- `spring.datasource.dynamic.enabled=true`（默认 `true`，可省略）。
- 存在 `spring.datasource.url` 时，自动创建名为 `master` 的默认数据源。
- 若 `spring.datasource.dynamic.datasource` 中也显式配置了 `master`，显式配置优先级更高。

切换数据源时使用 baomidou 的 `@DS("slave")` 注解。

## 接口文档约定

### OpenAPI 配置

引入 `frame-me-starter-doc-openapi` 并开启后，通过 `me.swagger` 前缀配置文档信息：

```yaml
me:
  swagger:
    enabled: true
    title: Frame Me API
    description: Frame Me 接口文档
    version: 1.0.0
    contact:
      name: Frame Me Team
      email: team@frame.me
      url: https://frame.me
    groups:
      - name: default
        paths-to-match:
          - /**
```

访问地址：

- API Docs：`/v3/api-docs`
- Swagger UI：`/swagger-ui.html`

未配置 `groups` 时，默认注册一个名为 `default`、匹配所有路径的分组。

## 认证与授权

### 认证抽象层 `frame-me-starter-auth`

类路径：`frame-me-starter-auth/src/main/java/com/frame/me/auth`

`frame-me-booter` 已默认引入 `frame-me-starter-auth`，业务 `xx-service` 无需额外配置即可获得以下能力：

- **`AuthContext`**：ThreadLocal 当前用户上下文，提供 `getUser()` / `getUserId()` / `getAccount()` / `setUser(User)` / `clear()`。
- **`@LoginUser`**：标注在 Controller 方法参数上，自动注入当前登录用户。
- **`@Anonymous`**：标注在 Controller 类或方法上，表示该接口允许匿名访问。
- **`AuthFilter`**：全局认证过滤器，默认拦截 `/*`。
  - 配置白名单路径（`me.auth.whitelist`）或 `@Anonymous` 注解可放行。
  - 非白名单请求未解析到用户时返回 401。
  - 配置 `me.auth.enforce-login=false` 可关闭强制登录，此时过滤器仅尝试解析用户并写入 `AuthContext`，不返回 401。
  - **Filter 层错误响应格式由 `IFilterErrorResponseWriter` SPI 决定**：`frame-me-starter-base` 默认输出 `Result` 格式；引入 `frame-me-adapter-starter` 后自动切换为外部 `Response` 格式，与 Controller 层的老接口规范保持一致。
- **`IAuthService` / `IAuthUserResolver`**：SPI 接口，供具体认证实现（如 JWT）接管。

示例：

```java
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @Anonymous
    @GetMapping("/public")
    public IResult<String> publicApi() {
        return Result.success("public");
    }

    @GetMapping("/private")
    public IResult<String> privateApi(@LoginUser User user) {
        return Result.success("hello " + user.getAccount());
    }
}
```

### JWT 认证实现 `frame-me-starter-auth-jwt`

类路径：`frame-me-starter-auth-jwt/src/main/java/com/frame/me/auth/jwt`

业务 `xx-service` 需要显式引入：

```xml
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-starter-auth-jwt</artifactId>
</dependency>
```

引入后自动接管 `IAuthService` / `IAuthUserResolver`，并提供默认接口：

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/auth/login` | POST | 账号密码登录，返回 Access Token + Refresh Token |
| `/api/auth/logout` | POST | 使当前 Access Token 对应的 Refresh Token 失效 |
| `/api/auth/refresh` | POST | 使用 Refresh Token 换取新的 Token 对 |
| `/api/auth/me` | GET | 获取当前登录用户信息 |

业务只需实现 `IAuthUserDetailsService`：

```java
@Service
public class UserDetailsServiceImpl implements IAuthUserDetailsService {

    @Override
    public User loadUserByAccount(String account) {
        // 按账号查询用户，返回包含加密密码的 User
    }

    @Override
    public User loadUserById(Long id) {
        // 按用户 ID 查询用户
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return PasswordUtils.matches(rawPassword, encodedPassword);
    }
}
```

### 配置示例

```yaml
me:
  auth:
    jwt:
      secret: your-secret-key-at-least-32-characters-long
      access-token-expires: PT2H
      refresh-token-expires: P7D
```

- `secret` **必须配置**，长度不少于 32 字符。
- Refresh Token 默认存储在 Redis，需配置 `spring.data.redis.*`。

### 关闭强制登录

默认情况下，非白名单请求未解析到用户时会返回 401。若希望过滤器只尝试解析用户、不拦截匿名请求（例如网关已做认证），可关闭强制登录：

```yaml
me:
  auth:
    enforce-login: false   # 默认 true
```

关闭后，未携带认证信息的请求也会被放行，但 `AuthContext` 中不会有用户；下游仍可通过 `AuthContext.getUser()` 是否为 null 判断当前是否已登录。

### 服务间调用传播用户信息

当使用 `@ImportHttpServices` / Spring HTTP Interface 进行服务间调用时，`frame-me-starter-auth` 会自动把当前请求的认证信息传播到下游服务，使下游无需改动即可通过 `@LoginUser` 获取同一用户。

传播范围：

- 仅作用于 `@ImportHttpServices` 生成的声明式 HTTP 客户端，不影响普通 `RestClient` / `RestTemplate`（避免把内部 token 泄露给外部 webhook 等调用）。
- 默认从当前请求原样传播以下头：
  - `Authorization`：覆盖 JWT（`Bearer ...`）场景。
  - `X-User-Id`、`X-User-Account`：覆盖 `HeaderAuthUserResolver` 场景。
- 若当前请求已通过认证（无论 JWT 还是 header-auth），还会从 `AuthContext` 把用户 ID 和账号作为 `X-User-Id` / `X-User-Account` 补充到出站请求，使 **JWT 上游调用 header-auth 下游** 的混合场景也能被识别。

配置示例：

```yaml
me:
  auth:
    propagate:
      enabled: true                    # 默认 true
      headers:                         # 需要从当前请求原样传播的头
        - Authorization
        - X-User-Id
        - X-User-Account
      user-info:
        enabled: true                  # 默认 true，从 AuthContext 补充 X-User-Id / X-User-Account
        user-id-header: X-User-Id
        user-account-header: X-User-Account
```

- `enabled`：是否启用传播，默认 `true`。
- `headers`：需要从当前请求原样传播到下游的头名列表。
- `user-info.enabled`：是否从 `AuthContext` 补充用户头，默认 `true`。
- `user-info.user-id-header` / `user-info.user-account-header`：用户 ID / 账号对应的头名，可自定义。

**注意：** 传播依赖当前线程是否绑定了 `HttpServletRequest`。如果在 `@Async`、定时任务等无线程绑定请求的上下文里发起调用，拦截器会静默跳过，不会传播认证头。

### 异步线程传播

如果需要在 `@Async` 方法内部继续使用 `AuthContext` 中的用户信息（例如 `@LoginUser`、审计等），或继续通过 `@ImportHttpServices` 向下游传播认证头，开启异步传播开关：

```yaml
me:
  auth:
    propagate:
      async:
        enabled: true   # 默认 true
```

**默认启用**；关闭后异步线程中无法获取 `AuthContext` 用户，下游调用也不会携带认证头。

传播内容：

- `AuthContext` 中的当前用户。
- `me.auth.propagate.headers` 配置的请求头（来自当前 `HttpServletRequest`）。

实现方式：默认异步线程池的 `TaskDecorator` 在任务提交时捕获当前上下文，并在异步线程执行前恢复。

**注意：** 异步传播与 header 传播开关（`me.auth.propagate.enabled`）相互独立。即使关闭 header 传播，只要 `me.auth.propagate.async.enabled=true`，异步线程中仍然能拿到 `AuthContext` 用户；但此时通过 `@ImportHttpServices` 调用下游服务不会携带认证头。

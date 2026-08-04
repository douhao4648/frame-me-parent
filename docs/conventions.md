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
| `requestId` | `String` | `Result.rid`（`rid` 暂无生产侧填充） |

`Response<T>` 实现了 `Serializable` 与 `IResult<T>`：接口 getter（`getMsg`/`getData`/`getRid`）委托到 `message`/`result`/`requestId` 字段，JVM 内可按 `IResult` 契约正常读取；这些接口方法与 `success` 均标记 `@JsonIgnore`，序列化报文只携带上表四个字段，不混入规范字段名。

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
| `handleBusinessException` | `BusinessException` | 默认 200 | `error` | `Result.error(code, message, exception)` / `Result.error(code, message)` |
| `handleInternalException` | `InternalException` | 500 | `error` | `Result.error(code, message, exception)` / `Result.error(code, message)` |
| `handleMethodArgumentNotValidException` | `MethodArgumentNotValidException`（`@RequestBody` 校验失败） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, 首条字段错误消息)` |
| `handleConstraintViolationException` | `ConstraintViolationException`（`@PathVariable`/`@RequestParam` 校验失败） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, 首条约束错误消息)` |
| `handleBindException` | `BindException`（表单/查询参数绑定失败） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, 首条字段错误消息)` |
| `handleHttpMessageNotReadableException` | `HttpMessageNotReadableException`（请求体缺失或不可读） | 默认 200 | `warn` | `Result.error(BAD_REQUEST, "请求体不能为空")` |
| `handleException` | `Exception` | 默认 200 | `error` | `Result.error(ERROR, message, exception)` / `Result.error(ERROR, message)`；`me.exception.mask-unknown-message=true` 时 message 收敛为通用文案（"系统错误"） |

是否把异常完整堆栈写入 `Result.err` 由 `me.exception.include-stacktrace` 控制，默认 `false`（fail-closed，避免堆栈中的类路径、参数等敏感信息随响应体泄漏）；排查问题时可显式设为 `true`，异常详情仍可通过服务端日志定位。

对外口径约定：`BusinessException` 与 `InternalException` 的 message 视为业务方有意对外，原样回传；兜底 `Exception` 的 message 来源不可控（可能携带 SQL、类路径、内网地址），默认仍回传（兼容原行为），对外服务建议配置 `me.exception.mask-unknown-message=true` 收敛为通用文案（真实 message 只进服务端日志）。

## 编码风格

- **包名**：`com.frame.me.<module>`，与 Maven 模块后缀一致。
- **Lombok**：使用 `@Data`、`@Getter`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@Slf4j`。
- **常量容器**：每个模块定义一个 `*Constant` 占位类，声明为 `final` 并私有化构造器（如 `BaseConstant`、`AuthConstant`），防止被实例化或 `implements` 滥用。
- **注释**：类级 Javadoc 使用中文。
- **类设计**：普通业务类保持默认可继承，不强制声明 `final`；工具类（仅含静态方法/常量、无实例状态）建议声明为 `final` 并私有化构造器，防止被实例化或继承。

## 模块装配约定

starter 模块对 **optional 依赖**（`<optional>true</optional>` 或 `provided` scope 的可选库，如 `multi-redis`、`redisson`、`freemarker`）必须做条件保护，确保消费方未引入该依赖时 starter 优雅退避/降级，不抛 `NoClassDefFoundError` / `ClassNotFoundException`。现有三种已验证模式，新模块按场景择一：

- **模式 A：`@ConditionalOnClass` + 隔离内部配置类**（推荐，optional 依赖提供替代实现时）
  - 把引用 optional 类的 Bean 装配放进**独立 `static` 内部配置类**，类上加 `@ConditionalOnClass`。Spring 在 ASM 阶段评估条件，optional 类缺席时整个内部类被跳过，其引用的 Bean 类不会被类加载器加载，从而不触发 NCDFE。
  - 外层配置类提供**降级 Bean**（`@ConditionalOnMissingBean` 兜底）。示例见 `frame-me-starter-auth-jwt` 的 `JwtAutoConfiguration`：optional `multi-redis` 缺席时 `RedisRefreshTokenStoreConfiguration` 跳过，`InMemoryRefreshTokenStore` 兜底。
  - `@ConditionalOnClass` 的 `value`（`.class`）与 `name`（字符串）在 ASM 评估下功能等价，均安全；**同一模块内尽量统一写法**（`RedissonLockAutoConfiguration` 用 `name`、`RedisEventTransportAutoConfiguration` 用 `.class`，混用不影响功能但不统一）。

- **模式 B：`ClassUtils.isPresent` + 反射加载**（optional 依赖提供增强能力、无替代实现时）
  - 装配期用 `ClassUtils.isPresent("fully.qualified.Name", classLoader)` 判定 optional 类是否在 classpath，在则用反射 `Class.forName(...).getDeclaredConstructor().newInstance()` 实例化增强实现，不在则跳过。
  - 配合**占位兜底实现**保证核心功能不依赖 optional 库。示例见 `frame-me-starter-msg-notify`：optional `freemarker` 缺席时跳过 `FreemarkerTemplateEngine`，`PlaceholderTemplateEngine` 兜底。

- **模式 C：`@ConditionalOnBean` 二次守卫**（optional 类在 classpath 但对应 Bean 可能未创建时）
  - 在模式 A/B 基础上叠加 `@ConditionalOnBean(X.class)`，确保依赖的 Bean 实际存在于容器才装配，避免"类在但 Bean 未注册"的运行期空指针。示例见 `RedisEventTransportAutoConfiguration` 的 `@ConditionalOnBean({RedissonClient.class, EventBridgeProperties.class})`。

- **配套测试**：每个 optional 依赖保护点必须有 **AbsentClasspath 测试**（用 `FilteredClassLoader` 屏蔽 optional 包模拟缺席），验证 starter 在缺类时整体退避不抛异常。示例见 `RbacRedisAbsentClasspathTest`、`RedisEventTransportAutoConfigurationTest#shouldNotConfigureTransportWhenRedissonJarAbsent`、`frame-me-starter-cloud` 的 `CloudAbsentClasspathTest`。

## 配置中心刷新解密约定

引入配置中心（如 Nacos）后，`ME(密文)` 的解密链路在启动期与运行时刷新两个时序上分别处理：

- **启动期（零改动）**：`spring.config.import` 触发的配置中心加载走 `ConfigDataEnvironmentPostProcessor`（order `HIGHEST_PRECEDENCE + 10`），远早于 sensi-encrypt 的 `EncryptablePropertyEnvironmentPostProcessor`（`LOWEST_PRECEDENCE`）。配置中心源在 sensi-encrypt 跑时已就位，sensi-encrypt 现有循环天然扫描到并原位包装成 `DecryptedPropertySource`。**启动期不需要额外监听器/PostProcessor。**
- **运行时刷新**：配置中心推送变更触发 `RefreshEvent` → `ContextRefresher.refreshEnvironment()` 构造全新 `PropertySource` 替换旧源（粒度为 Data ID / 整个配置文件级，非字段级）→ 发布 `EnvironmentChangeEvent`。`frame-me-starter-cloud` 的 `RefreshDecryptListener` 监听此事件，对新加入的未包装含密文源重新包装；已是 `DecryptedPropertySource` 的跳过（幂等）。**这是 sensi-encrypt 的 `EnvironmentPostProcessor` 只在启动跑一次、不重跑所必需的补全。**

约定：

- **配置中心无关**：`RefreshDecryptListener` 监听 `EnvironmentChangeEvent`，不绑定具体配置中心；任何走 Spring Cloud 刷新体系的配置中心（Nacos / Apollo / Consul）都自动被覆盖。解密参数（prefix / suffix）与 sensi-encrypt 共用同一套 `me.encrypt.*`，密文互通。
- **ME(密文) 不参与热刷新生效**：敏感值变更走重启。`@RefreshScope` 用于业务配置（开关、阈值）；**敏感配置 bean 不用 `@RefreshScope`**，避免短窗口期（`EnvironmentChangeEvent` 到监听器跑完之间密文裸露）读到密文。
- **主密码永不进配置中心**：`me.encrypt.password` 只走环境变量 / `-D` 启动参数，不写入 Nacos 远程配置（否则形成"解密自己"的循环依赖）。
- **连配置中心的凭证**（如 `spring.cloud.nacos.username/password`）写**本地** `application.yml`，可用 `ME(密文)`，sensi-encrypt 启动期解密；业务敏感值（`spring.datasource.password` 等）放配置中心远程配置写成 `ME(密文)`，运行时刷新由 `RefreshDecryptListener` 补全解密。

## 优雅下线约定

云平台滚动发布时，容器收到 SIGTERM 到真正被 kill 之间有窗口（K8s 默认 `terminationGracePeriodSeconds=30s`）。直接关闭 Tomcat 会导致 LB 健康检查未失败新请求继续打进来、注册中心实例还在消费者拉到调用失败、在途请求未处理完被 kill。`frame-me-starter-cloud` 提供**注册中心无关的优雅下线编排**（`me.cloud.shutdown.*`），编排多步骤：标记 health DOWN → 反注册 → 等待消费者刷新缓存 → Spring 原生 graceful shutdown 处理在途请求。

约定：

- **两条触发路径，幂等**：
  - **preStop 主路径**：K8s `preStop` 钩子调 `POST http://localhost:{management.port}/actuator/offline`，SIGTERM 前完成整个下线编排（同步阻塞，返回后 K8s 才发 SIGTERM）。需 `management.endpoints.web.exposure.include` 含 `offline`。
  - **ContextClosedEvent 兜底**：未配 preStop 或 preStap 失败时，SIGTERM 触发 Spring 关闭流程，`GracefulShutdownListener` 在 Tomcat graceful shutdown 之前完成编排。preStap 调过端点后 flag 已 false、已反注册，listener 再跑一遍无副作用。
- **health 联动两处**：
  - actuator `/actuator/health`：`ShutdownHealthIndicator`（Boot 4 新包 `org.springframework.boot.health.contributor`）读 flag，false → `OUT_OF_SERVICE`（HTTP 503），打 management 端口的探针立即摘流。
  - 业务 HealthController：注入 `ShutdownReadyFlag`，flag false → 返回 503 DOWN，打业务端口的探针也摘流。两条探针路径都覆盖。
- **反注册用 `ObjectProvider` 守卫**：无注册中心（纯定时任务服务、或未引 discovery）时 `ServiceRegistry`/`Registration` Bean 不存在，跳过反注册，只做 health 联动 + 等待——不抛异常。
- **配置示例**（业务 `application.yml`）：
  ```yaml
  server:
    shutdown: graceful                              # 原生：处理在途请求
  spring:
    lifecycle:
      timeout-per-shutdown-phase: 30s                # 原生：关闭超时，与 K8s terminationGracePeriodSeconds 对齐
  me:
    cloud:
      shutdown:
        enabled: true
        deregister-wait: 15s
        health-indicator-enabled: true
        endpoint-enabled: true
  management:
    endpoints:
      web:
        exposure:
          include: health,offline                   # 暴露 health 与 offline 端点
  ```
- **K8s 对齐建议**：`terminationGracePeriodSeconds` ≥ `deregister-wait`(15s) + `timeout-per-shutdown-phase`(30s) + buffer(5s) = **50s**。否则 Tomcat 还在处理在途请求就被 SIGKILL，graceful shutdown 失效。
- **preStop 示例**（K8s deployment）：
  ```yaml
  lifecycle:
    preStop:
      exec:
        command: ["sh", "-c", "curl -X POST http://localhost:9091/actuator/offline && sleep 1"]
    terminationGracePeriodSeconds: 50
  ```

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

字段值解析优先级：JavaBean getter（`getXxx` / `isXxx`）→ 同名无参方法（record / 流式访问器）→ 声明字段直读（含父类）；字段不存在时不校验（静默通过）。

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

> **排序字段安全**：`PageUtils` 与 `PageableUtils` 对客户端传入的排序字段统一按白名单 `^[A-Za-z0-9_.]+$` 校验（允许 `table.column` 限定名），非法字段静默丢弃，防止 ORDER BY SQL 注入；列名在 SQL 中无法参数化，业务侧切勿绕过工具类直接拼接排序字段。

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

`frame-me-boot` 已默认引入 `frame-me-starter-auth`，业务 `xx-service` 无需额外配置即可获得以下能力：

- **`AuthContext`**：ThreadLocal 当前用户上下文，提供 `getUser()` / `getUserId()` / `getAccount()` / `setUser(User)` / `clear()`。
- **`@LoginUser`**：标注在 Controller 方法参数上，自动注入当前登录用户。
- **`@Anonymous`**：标注在 Controller 类或方法上，表示该接口允许匿名访问。
- **`AuthFilter`**：全局认证过滤器，默认拦截 `/*`。
  - 配置白名单路径（`me.auth.whitelist`）或 `@Anonymous` 注解可放行。白名单按**应用内路径**匹配（不含 `server.servlet.context-path`，配置误带 context-path 前缀时自动剥离兼容）。
  - 非白名单请求未解析到用户时返回 401。
  - 配置 `me.auth.enforce-login=false` 可关闭强制登录，此时过滤器仅尝试解析用户并写入 `AuthContext`，不返回 401。
  - **ERROR dispatch 直接放行**：`FilterRegistrationBean` 默认挂 REQUEST/ERROR/ASYNC 三类 dispatch，过滤器对 ERROR（容器 `/error` 转发）不做任何校验与上下文清理——避免 404、servlet 级异常的真实状态码被 401 掩盖；`AuthContext` 由 REQUEST dispatch 的 finally 负责清理。
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
| `/api/auth/logout` | POST | 使当前 Access/Refresh Token 对应的 Refresh Token 失效；也支持直接传入 Refresh Token 进行清除 |
| `/api/auth/refresh` | POST | 使用 Refresh Token 换取新的 Token 对 |
| `/api/auth/user` | GET | 获取当前登录用户信息 |

业务只需实现抽象层 `com.frame.me.auth.spi.IAuthUserDetailsService`（密码工具 `com.frame.me.auth.util.PasswordUtils` 同在抽象层 `frame-me-starter-auth`）：

```java
import com.frame.me.auth.spi.IAuthUserDetailsService;

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

    // matches 为 default 方法（PasswordUtils BCrypt 校验），换算法时才需覆盖
}
```

「查用户 → 空则 401 → 校验密码 → 失败 401」的认证步骤由抽象层
`com.frame.me.auth.core.AuthUserAuthenticator.authenticate(...)` 承载，
各认证实现的 `login` 直接调用，不再各自复制。用户不存在时也会对哑 BCrypt hash
执行一次同等耗时的 `matches`，消除响应时间差导致的账号枚举（对齐 Spring Security
`userNotFoundPassword` 机制），实现 `matches` 时不应自行对空 hash 短路。

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

### Sa-Token 认证实现 `frame-me-starter-auth-sa-token`

类路径：`frame-me-starter-auth-sa-token/src/main/java/com/frame/me/auth/satoken`

基于 sa-token（`cn.dev33:sa-token-spring-boot4-starter`，1.45.0）的会话治理型认证实现，面向需要踢人 / 封禁 / 在线会话 / 多端互斥的后台场景。**不纳入 `frame-me-boot`**，业务 `xx-service` 显式引入：

```xml
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-starter-auth-sa-token</artifactId>
</dependency>
```

引入后自动接管 `IAuthService` / `IAuthUserResolver`，并提供默认接口（与 JWT 模块同形状；基础路径 `me.auth.sa-token.path`，默认 `/api/auth`）：

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/auth/login` | POST | 账号密码登录，创建 sa-token 会话并返回 token |
| `/api/auth/logout` | POST | 注销当前 token 对应的会话 |
| `/api/auth/refresh` | POST | 对当前 token 续绝对有效期并重置闲置冻结窗口，返回原 token |
| `/api/auth/user` | GET | 获取当前登录用户信息 |

- Token 优先从原生 `sa-token.token-name` 指定的请求头读取（默认 `satoken`），header 缺失时按同名 Cookie 兜底读取（与 sa-token 原生 is-read-cookie 行为对齐）。
- `TokenVO.refreshToken` 恒为 `null`：sa-token 会话模型无 Refresh Token 概念。
- **原生 Cookie 支持**：`sa-token.is-read-cookie` 默认 `true`——登录自动写 Cookie、登出自动清、续期自动刷；Cookie 名取 `sa-token.token-name`，属性（domain/path/secure/http-only/same-site）全部来自 `sa-token.cookie.*`，Max-Age 由 `is-lasting-cookie` + `timeout` 派生；Token 同时永远经 JSON body 返回，前端双通道二选一。设 `sa-token.is-read-cookie=false` 可整体关闭 Cookie 通道。
- 业务接入只需实现抽象层 `com.frame.me.auth.spi.IAuthUserDetailsService`，与 JWT 实现共用同一份业务实现。

配置示例（常用项；全量配置与默认值见 `docs/modules.md` 的 sa-token 小节）：

```yaml
# sa-token 原生参数走官方 sa-token.* 配置路径（秒数 long 形式，详见 sa-token 官方文档）
sa-token:
  token-name: satoken        # token 请求头名（兼 Cookie 名与存储 key 前缀），默认 satoken
  timeout: 604800            # token 绝对有效期（秒），默认 2592000（30 天）；≈ JWT refresh-token-expires
  active-timeout: 7200       # 闲置冻结窗口（秒），默认 -1 不限制；≈ JWT access-token-expires
  is-concurrent: true        # 同账号多地共存，默认 true；false 时新登录挤掉旧登录
  # is-read-cookie: true     # 原生 Cookie 读写开关，默认 true：登录写 Cookie、登出清、续期刷
  # cookie:                  # 原生 Cookie 属性（可选）
  #   domain: .example.com
  #   http-only: true
  #   same-site: Lax

# 本模块仅承载框架自有配置
me:
  auth:
    sa-token:
      path: /api/auth          # 认证接口基础路径，默认 /api/auth
      authorization:
        enabled: true          # 是否启用 sa-token 原生鉴权（路径规则 + @SaCheck* 注解），默认 true
      jwt:
        enabled: false         # 是否启用 JWT Token 模式，默认 false；开启后需显式引入 sa-token-jwt 并配置 sa-token.jwt-secret-key
      rules:                     # 路径级鉴权规则（⚠️ key 必须用方括号记法，见下）
        "[/api/admin/**]": "role:admin"
        "[/api/order/**]": "perm:order:read"
      roles:                     # 角色 -> 权限码（配置版权限数据源）
        admin: "user:add,order:read"
      users:                     # 用户 ID(字符串) -> 角色
        "1": "admin"
```

鉴权用法（sa-token 原生能力）：

- **方法级注解**：`@SaCheckLogin` / `@SaCheckRole("admin")` / `@SaCheckPermission("order:read")`，由自动装配注册的 `SaInterceptor` 使其生效。
- **路径级规则**：`me.auth.sa-token.rules`，value 为简化表达式（非 SpEL）：`login`、`role:xxx`、`perm:resource`、`perm:resource:action`。**⚠️ YAML 中 key 必须用方括号记法** `"[/api/admin/**]"`——Spring Boot 对 `Map` key 做 relaxed binding 时会剥离 `/`、`*`，导致规则匹配不上；启动时校验到不以 `/` 开头的 key 会直接抛异常，阻止应用启动（fail-fast）。
- **权限数据源**：默认配置版（`me.auth.sa-token.users` / `roles`）；业务声明任意 `StpInterface` Bean 即接管（接数据库时应自行缓存——sa-token 每次鉴权都会回调该接口）。

401 / 403 语义：

- Filter 层（`AuthFilter`）未登录仍返回 401，与 JWT 模式一致。
- 注解与路径规则层抛出的 sa-token 异常由 `SaTokenExceptionAdvice` 映射：`NotLoginException` → 401；`NotRoleException` / `NotPermissionException` / `DisableServiceException` → 403（统一 `Result`，HTTP 200、业务码在 body）。返回给客户端的 `msg` 固定使用 `ResultCode` 通用文案（如“未授权”“禁止访问”），异常原始消息仅记录日志，避免信息泄露。

**选型：jwt + rbac vs sa-token**

| 维度 | `frame-me-starter-auth-jwt` + `frame-me-starter-auth-rbac` | `frame-me-starter-auth-sa-token` |
|---|---|---|
| Token 模型 | 无状态 JWT（access）+ Redis 持久化 refresh | 不透明 token + 服务端会话（内存 / Redis） |
| 单请求开销 | 本地验签，零 I/O | 每请求查会话存储（Redis 模式下有网络 I/O） |
| 会话治理 | 无（仅 refresh token 吊销） | 踢人、封禁、在线会话、多端互斥（`is-concurrent=false`）、闲置冻结（`active-timeout`） |
| 权限模型 | RBAC + 数据权限（行级数据范围） | 角色 + 权限码（sa-token 原生），**无数据权限** |
| 鉴权写法 | `@RequireAuth`(SpEL) + Filter rules | `@SaCheck*` 注解 + Interceptor rules |
| 适用场景 | 无状态 API、需要数据权限的业务系统 | 需要会话治理的后台 / 管理系统 |

需要数据权限的项目选 jwt + rbac；需要踢人 / 封禁 / 在线会话治理的项目选 sa-token。

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
      allowed-hosts:                   # 目标主机白名单，默认空
        - "*.internal.example.com"
      service-discovery:
        enabled: true                  # 默认 true，服务名调用豁免（见下）
      user-info:
        enabled: true                  # 默认 true，从 AuthContext 补充 X-User-Id / X-User-Account
        user-id-header: X-User-Id
        user-account-header: X-User-Account
```

- `enabled`：是否启用传播，默认 `true`。
- `headers`：需要从当前请求原样传播到下游的头名列表。
- `allowed-hosts`：允许注入认证头的目标主机白名单，支持精确主机名（不区分大小写）、`*.example.com` 后缀通配与 `*` 全匹配，默认空。
- `service-discovery.enabled`：服务名调用豁免，默认 `true`。开启时目标主机被甄别为**注册中心服务名**（classpath 存在 Spring Cloud 时以 `LoadBalancerClient.choose(host)` 判定，覆盖 `order.default.svc.cluster.local` 等 K8s 全限定名）或**单标签内网主机名**（不含 `.`，如 `order-service`、`localhost`）即允许传播，无需配置白名单；外部多标签域名/IP 一律不传播。关闭后仅严格按 `allowed-hosts` 白名单传播。探针通过专用 SPI `IServiceInstanceProbe` 注入（避免与容器中其他通用 `Predicate<String>` bean 冲突），业务方可注册自定义实现覆盖默认的 LoadBalancer 探针。
- 即默认配置下：内部服务名调用零配置照常传播，第三方地址拿不到 `Authorization` / `X-User-Id`；有非服务名的内网调用（直连 IP/域名）时配置 `allowed-hosts` 补充。
- **前提**：拦截器先于 LoadBalancer 拦截器执行（此时 URI host 仍是服务名），Spring Cloud 默认装配顺序满足该前提；若项目自定义了拦截器顺序导致 LB 先解析为 IP，服务名调用会被误判为外部而不传播。
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

### 权限控制

`frame-me-starter-auth-rbac` 提供基于角色 + 资源/操作的轻量级权限控制，不依赖 Spring Security，需要搭配 `frame-me-starter-auth` 使用。

#### 权限模型

- **角色（Role）**：字符串标识，如 `admin`、`operator`。
- **权限（Permission）**：由 `resource`（资源）和 `action`（操作）组成，如 `user:read`、`order:create`；支持 `*` 通配。
- **数据权限（Data Permission）**：由 `resource` + `action` + `dataScope` + `dataIds` 组成。`dataScope` 取值 `ALL`（全部数据）/`DEPT`（本部门）/`ORG`（本机构）/`SELF`（本人）/`CUSTOM`（指定数据行）;`dataIds` 为 `CUSTOM` 范围的**资源行主键集合**。三种使用方式见下方「数据权限」小节。

#### 权限数据源

默认使用配置化数据源；业务可声明自定义 `IAuthPermissionProvider` bean 接入数据库或远程服务。

配置示例（`Map<String, String>`，值为逗号分隔）：

```yaml
me:
  auth:
    permission:
      enabled: true
      rules:                          # Filter 层：Ant 路径 -> SpEL 表达式（key 必须用方括号记法，见下方告警）
        "[/api/admin/**]": "role('admin')"
        "[/api/order/**]": "role('admin') and perm('order', 'r')"
      roles:                          # 角色 -> 权限，逗号分隔 resource:action（action 可省略，默认 *）
        admin: "user:*,order,order:r"
        operator: "order:r"
      users:                          # 用户 ID(字符串) -> 角色，逗号分隔
        "1": "admin"
```

#### 方法级注解 `@RequireAuth`

在 Controller 类或方法上标注 `@RequireAuth("SpEL")`，表达式求值为 `true` 放行。可用函数：

| 表达式 | 说明 |
|---|---|
| `role('admin')` | 拥有指定角色 |
| `perm('user')` | 拥有 `user` 资源的**任意**操作权限 |
| `perm('user', 'w')` | 拥有 `user` 资源的指定操作（`w`）权限 |
| `dataIsAll('order')` / `dataIsAll('order', 'r')` | 拥有 `order` 资源的 `ALL` 数据范围（可带操作） |
| `dataCheck('order', #id)` / `dataCheck('order', 'r', #id)` | 可访问 `order` 资源的某条数据（命中 `ALL` 范围或 `dataIds` 并集；可带操作） |

表达式内可用 `#变量名` 引用 **URI 路径变量**（如 `@RequireAuth("dataCheck('order', #id)")` 搭配 `@GetMapping("/{id}")`）与**查询参数**（`@RequestParam`，含 POST 表单；单值给 `String`、多值给 `String[]`)，拦截器会自动注入；**同名时路径变量优先**（校验值必须与 `@PathVariable` 实际绑定值一致，防 `/api/order/5?id=6` 校验与执行不一致的越权窗口）。变量值为字符串，`dataId` 参数支持数字字符串自动转换。`@RequestBody` 的 DTO 在拦截器阶段尚未解析（body 流只能读一次），不在变量来源内——这类接口改用 `AuthDataPermissions.check` 在方法体内校验。

可用 `and` / `or` 组合；类与方法同时标注时方法优先，方法未标注则回退到类级。`@RequireAuth("true")` 恒真字面量是「纯加载开关」：登录即放行并触发权限加载（未登录仍 401)，适用于无功能权限要求、但方法体内要用 `AuthDataPermissions` 的接口。

示例：

```java
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @RequireAuth("role('admin')")
    @DeleteMapping("/{id}")
    public IResult<Boolean> delete(@PathVariable Long id) {
        // 仅 admin 可访问
    }

    @RequireAuth("perm('order', 'r')")
    @GetMapping("/{id}")
    public IResult<OrderVO> get(@PathVariable Long id) {
        // 拥有 order:r 权限可访问
    }

    @RequireAuth("role('admin') or perm('order')")
    @PostMapping
    public IResult<Long> create(@RequestBody OrderDTO dto) {
        // admin 或拥有 order 任意权限可访问
    }
}
```

#### Filter 层路径规则

按 URL 路径批量控制：

```yaml
me:
  auth:
    permission:
      rules:
        "[/api/admin/**]": "role('admin')"
        "[/api/order/**]": "role('admin') and perm('order', 'r')"
```

> **⚠️ 规则 key 必须用方括号记法。** Spring Boot 对 `Map` 的 key 做 relaxed binding 时，会剥离除字母数字、`-`、`.` 之外的字符——直接写 `/api/admin/**` 会被转成 `apiadmin`，规则匹配不上而**静默失效（fail-open，全部放行）**。务必写成 `"[/api/admin/**]"`。`RbacProperties` 启动时会校验：若有规则 key 不以 `/` 开头，会打 WARN 提示。

#### 响应语义

统一走 `Result`（HTTP 状态码恒为 200，业务码在 body 的 `code` 字段）：

- 未登录访问受保护接口：**401**（未认证）。
- 已登录但无权限：**403**（无权限）。
- 未标注 `@RequireAuth`、未命中路径规则的接口不干预（登录校验交由 `AuthFilter`）。

#### 运行时判断

在 Filter / Interceptor 之外的代码（如 Service）中，可直接读取当前请求的权限上下文：

```java
if (AuthPermissionHolder.getRoles().contains("admin")) {
    // ...
}
boolean canWrite = AuthPermissionHolder.getPermissions().stream()
        .anyMatch(p -> p.matches("order", "w"));
```

> 注意：`AuthPermissionHolder` 由 Filter/Interceptor 在命中权限校验时按需加载，未命中权限规则的普通接口调用前不会主动加载。加载是原子写入：provider 中途抛异常（DB/Redis 故障）不留半加载状态，避免线程复用时读到上一个用户的残留角色。

#### 数据权限

在功能权限（能不能调这个接口）之外，数据权限控制「能看到哪些行」。两种使用方式共享同一套合并语义（`DataPermissionResolver`：同一资源任一 `ALL` → 全部放行；否则 scope 取并集、`dataIds` 取并集）。

> 历史：曾提供「SQL 自动拦截」方式（按表规则改写 SQL),因 MyBatis-Plus 与 MyBatis-Flex 能力不对等（官方拦截器 vs 自写改写器）、fail-open 边界多，已移除；列表过滤统一用方式二显式拼条件。

**方式一：SpEL 单条校验** —— 详情/编辑/删除等单条操作接口：

```java
@RequireAuth("dataCheck('order', #id)")
@GetMapping("/api/order/{id}")
public IResult<OrderVO> get(@PathVariable Long id) { ... }
```

**方式二：Service 层静态 Helper** —— 业务自行拼查询条件：

```java
if (AuthDataPermissions.isAll("order")) {
    // 不加条件
} else if (AuthDataPermissions.scopes("order").contains(IDataScopes.SELF)) {
    query.eq(Order::getCreatedBy, AuthContext.getUserId());
} else {
    query.in(Order::getId, AuthDataPermissions.dataIds("order")); // CUSTOM
}
boolean visible = AuthDataPermissions.check("order", orderId); // 单条判定
```

**单条编辑/删除的归属校验（`SELF` 场景）** —— 先查行，再 `checkOwner` 比对行归属字段：

```java
@RequireAuth("role('saler')") // 加载点
@PutMapping("/api/order/{id}")
public IResult<Void> update(@PathVariable Long id, @RequestBody OrderUpdateDTO dto) {
    Order order = orderService.getById(id);
    if (order == null) {
        return IResult.error(ResultCode.NOT_FOUND);
    }
    // ALL 放行;SELF 且 order.createdBy == 当前用户放行;否则拒绝
    if (!AuthDataPermissions.checkOwner("order", order.getCreatedBy())) {
        throw new BizException(ResultCode.FORBIDDEN);
    }
    ...
}
```

行归属在行数据里而不在权限快照里，单条 `SELF` 判定必须查一次行——编辑/删除接口本来就要查，无额外成本，数据量大也不需枚举 `dataIds`。

**自定义数据权限数据源**：配置版只能提供 scope；`CUSTOM` 的动态 `dataIds`（如按客户分配）需自定义 provider 实现 `IAuthPermissionProvider#getDataPermissions(User)` 返回 `DataPermission(resource, action, "CUSTOM", ids)`。bean 命名与 `@Primary` 约束同功能权限（启用 Redis 后端时必须命名 `authPermissionSource`）。数据权限与角色/权限一起经 `AuthPermissionHolder` 请求级缓存、`RedisAuthPermissionProvider` 快照缓存与 `@Async` 传播，自定义 provider 每请求最多被调用一次。

**上下文生命周期**：方式一/二经 `AuthPermissionHolder` 读取数据权限——Helper **纯读、自身不触发加载**，加载只发生在三个边界加载点（`PermissionFilter` 命中 rules、`PermissionInterceptor` 见 `@RequireAuth`、`@Async` 经 `AuthPermissionTaskDecorator` 传播）；请求未经过任一加载点时，Helper 按「无数据权限」处理（fail-closed 空结果，不报错）。加载后的清理由请求边界组件负责（`PermissionFilter` finally、`PermissionInterceptor.afterCompletion`;`@Async` 由 `AuthPermissionTaskDecorator`)，业务无需也不应手动清理。唯一例外：非 web 线程（如 `@Scheduled`）手动 `AuthContext.setUser(...)` 模拟身份执行时，用完须自行 `AuthContext.clear()`（ThreadLocal"谁 set 谁 clear"惯例）。

**注意**:

- `check` 单条校验只对 `ALL`/`CUSTOM` 有意义；`DEPT`/`ORG`/`SELF` 范围对单条判定恒 false（行与部门/用户的从属关系无法从单条 ID 推出）。`SELF`/`DEPT`/`ORG` 的单条归属校验分别用 `checkOwner`/`checkDept`/`checkOrg`（先查行再比对，见上）；列表过滤用方式二按 scope 拼条件。
- `CUSTOM` + `dataIds` 适合「人工分配的小集合」（如分配给销售的几十个客户）;「我的数据」这类大集合按归属判定，用 `SELF`（列表 Helper 拼 `created_by` 条件、单条 `checkOwner`)，不要把全量行主键塞进 `dataIds`。
- Redis 后端快照升级：旧缓存 JSON 无 `dataPermissions` 字段，反序列化为空列表，升级后数据权限为空直至 TTL 过期或 `evict`。
- **Helper 不自动加载是刻意决策**（非遗漏）：静态 Helper 拿不到 `IAuthPermissionProvider`（加载走 DI 边界），「谁加载谁清理」由 `PermissionFilter` finally 无条件 `clear()` 兜底。首用懒加载在 web 线程技术上可行，但需引入静态 provider 全局槽、非 web 线程依旧无解、权限 I/O 从边界固定一次变为 Service 深处不确定位置一次——故维持「显式加载点」契约：**用 Helper 的接口要么标 `@RequireAuth`（无功能限制时用 `"true"` 字面量），要么路径命中 rules**。

#### 与 `@Anonymous` 的关系

- 标注了 `@Anonymous` 的类或方法自动跳过 `@RequireAuth` 拦截；但若命中 Filter 层路径规则，仍受规则约束。
- 未标注 `@Anonymous` 的接口，未登录时由 `AuthFilter` 返回 401；已登录但无权限时由权限层返回 403。
- 关闭 `me.auth.enforce-login` 后，未登录请求会放行到权限层；标注 `@RequireAuth` 的方法因取不到用户而返回 401。

#### 异步上下文传播

`@Async` 方法内的 `role()/perm()` 判断依赖 `AuthPermissionHolder` 跨线程传播。装配 `AuthPermissionTaskDecorator`（默认开启，`me.auth.permission.propagate.async.enabled`）后，默认异步线程池会在任务提交时捕获权限上下文、在异步线程恢复并在执行后清理。base 的 `AsyncAutoConfiguration` 会把多个 `TaskDecorator`（含 `AuthContextTaskDecorator`、本装饰器）按序组合成链。

#### 可选 Redis 权限后端

`frame-me-starter-auth-rbac` 内置可选的 Redis 权限后端：**显式引入 `frame-me-starter-multi-redis` 即激活**(`RbacRedisAutoConfiguration` 以 `@ConditionalOnClass(RedisUtils.class)` 门控，multi-redis 在 auth-rbac 中为 optional 依赖，不引入则 classpath 零 Redisson)。激活后 `RedisAuthPermissionProvider` 以 `@Primary` 生效，做 read-through 缓存：**L1 本地缓存（Caffeine，短 TTL）→ L2 Redis → 委托数据源**。所有服务共享同一 Redis 时 RBAC 判定天然一致，支持权限新鲜与吊销。

- 委托数据源插槽 `authPermissionSource` 由 `RbacAutoConfiguration` 注册，默认 `ConfigAuthPermissionProvider`；声明同名 `IAuthPermissionProvider` bean 可接入数据库等真实数据源。启用 Redis 后端时业务 provider **必须**命名为 `authPermissionSource`（且不标 `@Primary`)，未命名会导致启动 fail-fast 而非静默忽略。
- 权限变更后调用 `RedisAuthPermissionProvider#evict(userId)` 失效缓存（L1 + L2）。
- Redis 读写异常时自动降级为直接回源（记告警日志），不影响权限校验主链路。

配置项 `me.auth.permission.redis.*`：`enabled`（默认 `true`）、`keyPrefix`（默认 `auth:perms:`）、`clientName`（默认 `default`）、`redisTtl`（默认 `30m`）、`localTtl`（默认 `5s`）、`localMaxSize`（默认 `10000`）。

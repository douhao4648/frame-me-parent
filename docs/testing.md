# 测试与运行

## 测试现状

当前项目中存在的测试位于：

```
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/ApplicationTests.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/AbstractIntegrationTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/async/AsyncCustomPrefixTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/async/AsyncIntegrationTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/auth/JwtAuthEndToEndTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/auth/PermissionIntegrationTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/cache/DemoServiceCacheTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/encrypt/JasyptEncryptTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/event/UserCreatedEventFlowTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/flex/FlexMultiDataSourceTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/DemoMapperIntegrationTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusCrudAndFillTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusLogicDeleteTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusOptimisticLockTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusPaginationTest.java
frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/redis/RedissonLockTest.java
```

- `AsyncCustomPrefixTest`：验证自定义 `@Async` 线程池前缀。
- `AsyncIntegrationTest`：验证默认 `@Async` 线程池与异常通知行为。
- `JwtAuthEndToEndTest`：验证 JWT 登录/刷新/当前用户/登出端到端流程（JWT 双 token 语义，切换为 sa-token 依赖后需排除编译）。
- `PermissionIntegrationTest`：覆盖 `@RequireAuth` 注解权限与 Filter 层路径规则（依赖 auth-rbac，切换为 sa-token 依赖后需排除编译）。
- `DemoServiceCacheTest`：演示 JetCache 两级缓存集成测试。
- `JasyptEncryptTest`：演示 Jasypt 配置加密解密测试。
- `UserCreatedEventFlowTest`：演示事件桥接端到端测试。
- `FlexMultiDataSourceTest`：演示 MyBatis-Flex + dynamic-ds 多数据源切换。
- `RedissonLockTest`：演示 Redisson 分布式锁集成测试。
- `ApplicationTests`：使用 H2 内存数据库验证 Spring Boot 上下文能正常启动，不依赖 Docker。
- `AbstractIntegrationTest`：Testcontainers + MySQL 集成测试基类。
- `DemoMapperIntegrationTest`：覆盖插入/自动填充、查询、乐观锁、逻辑删除、分页。
- `MybatisPlusCrudAndFillTest`：覆盖 CRUD 与自动填充。
- `MybatisPlusLogicDeleteTest`：覆盖逻辑删除行为。
- `MybatisPlusOptimisticLockTest`：覆盖乐观锁版本递增与冲突。
- `MybatisPlusPaginationTest`：覆盖分页插件与条件分页。

其他模块目前测试代码较少；`frame-me-starter-base` 包含 `EnvironmentHelperTest`、`SnowflakeUtilsTest` 等基础单元测试，以及 `PoolingRestClientAutoConfigurationTest`——验证池化 HTTP 客户端配置（观测某个调用用了哪个池：飞行途中断言共享 `PoolingHttpClientConnectionManager.getTotalStats().getLeased()`；并覆盖 `me.restclient.pool.*` / `spring.http.clients.*` / `spring.http.serviceclient.<group>.*` 三层配置叠加）。

`frame-me-sso-service` 包含 `SsoAuthFlowTest`：Testcontainers Redis + H2 + TestRestTemplate 真实 HTTP 的授权码全流程端到端测试（登录页匿名可访问、未登录 302、登录→发 code（state 回显）→换 token→Bearer /userinfo、授权码重放拒绝、scope/redirectUri 白名单 400、EXTERNAL 密钥强制校验、应用注册/更新 DTO 校验、client_credentials 应用 token 颁发（INTERNAL/EXTERNAL 均强制 secret）与其调 /userinfo 被拒、管理端点设备闸（应用 token 塞 satoken 头 403）、按 appId 踢人、禁用应用联动踢存量会话、用户 CRUD 全生命周期（重复账号/垃圾入参拒绝、VO 无密码字段）、用户更新校验与防自锁（禁用/删除当前登录账号拒绝）、改密码/禁用联动踢会话、用户管理端点设备闸拦截应用 token）。Docker 不可用时自动跳过，`ContextLoadTest` 仍会执行。

> 注意：Boot 4 的 `TestRestTemplate`（`spring-boot-resttestclient` 模块）**默认跟随重定向**，断言 302 需 `rest.withRedirects(HttpRedirects.DONT_FOLLOW)`；且需显式 `@AutoConfigureTestRestTemplate` 才有 `TestRestTemplate` Bean。单模块 `-pl` 跑测试时注意先 `install` 同工程 api 模块，否则本地仓库旧 jar 会造成接口签名不一致。

## 运行测试

### 运行全部测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home mvn test
```

Maven 会按 reactor 顺序编译所有模块，最后执行 `frame-me-tester` 中的测试。

如果本地没有 Docker，`DemoMapperIntegrationTest` 中的测试会自动跳过，`ApplicationTests` 仍会正常执行。

### 运行单个测试类

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  mvn -pl frame-me-tester/frame-me-tester-service test -Dtest=ApplicationTests
```

### 运行单个测试方法

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  mvn -pl frame-me-tester/frame-me-tester-service test -Dtest=ApplicationTests#contextLoads
```

### 跳过测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home mvn clean compile -DskipTests
```

## 认证实现切换（jwt / sa-token）

`frame-me-tester-service` 默认直接声明 `frame-me-starter-auth-jwt` + `frame-me-starter-auth-rbac` 依赖，使用 JWT + RBAC 认证。切换到 sa-token 会话治理型认证时，按 `pom.xml` 中依赖注释的指引操作：

1. 注释掉 `frame-me-starter-auth-jwt` 与 `frame-me-starter-auth-rbac` 依赖；
2. 取消 `frame-me-starter-auth-sa-token` 依赖的注释；
3. 排除两个不兼容测试的编译：`PermissionIntegrationTest`（依赖 auth-rbac 的 `@RequireAuth`，sa-token 模式下无法编译）与 `JwtAuthEndToEndTest`（断言 JWT 双 token 行为，sa-token 会话模型 `refreshToken` 恒为 `null`，必然失败）。

sa-token 模式的运行配置已预置在 `application.yml`：框架自有配置在 `me.auth.sa-token.*` 块，sa-token 原生参数（`token-name` / `timeout` / `active-timeout` 等）在根级 `sa-token.*` 块（jwt 模式下均被忽略）。

## 启动应用

### 通过 Maven 启动

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  mvn -pl frame-me-tester/frame-me-tester-service spring-boot:run
```

### 运行打包后的 Jar

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  mvn -pl frame-me-tester/frame-me-tester-service package

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  java -jar frame-me-tester/frame-me-tester-service/target/frame-me-tester-service-1.0.0-SNAPSHOT.jar
```

## 运行时配置

配置文件路径：`frame-me-tester/frame-me-tester-service/src/main/resources/application.yml`

```yaml
server:
  port: 9090

management:
  server:
    port: 9091

spring:
  application:
    name: frame-me-tester
```

- 访问端口：`9090`（管理端口 `9091`）
- 应用名称：`frame-me-tester`
- 当前未配置其他 profile 或外部配置中心。

## 示例接口

`HealthController` 映射到 `/health`：

```java
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Result<String> health() {
        String text = null;
        return Result.success(text.toUpperCase());
    }
}
```

注意：该实现故意对 `null` 调用 `toUpperCase()`，会触发 `NullPointerException`，用于验证全局异常处理链路。

## 测试约定

- 集成测试使用 `@SpringBootTest`。
- 测试类放在对应模块的 `src/test/java` 下。
- 新模块的单元测试建议使用 JUnit 5（已随 `spring-boot-starter-test` 引入）。
- 没有独立的 lint 命令，测试是代码正确性的主要验证手段。

## 集成测试（Testcontainers + MySQL）

`frame-me-tester` 模块包含基于 **Testcontainers 2.0.5 + MySQL 8.0** 的 MyBatis-Plus 集成测试，使用真实 MySQL 容器验证数据库行为。

### 前置条件

- 本地已安装并运行 **Docker**（或 Docker Desktop / OrbStack / Colima 等兼容实现）。
- 首次运行会自动拉取 `mysql:8.0` 镜像，耗时约 1-3 分钟（取决于网络）。

### 测试类

| 测试类 | 路径 | 说明 |
|---|---|---|
| `AbstractIntegrationTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/AbstractIntegrationTest.java` | 测试基类，负责启动 MySQL 容器并注入数据源配置 |
| `JwtAuthEndToEndTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/auth/JwtAuthEndToEndTest.java` | 覆盖 JWT 登录/刷新/当前用户/登出 |
| `PermissionIntegrationTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/auth/PermissionIntegrationTest.java` | 覆盖 `@RequireAuth` 注解权限与 Filter 路径规则 |
| `DemoMapperIntegrationTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/DemoMapperIntegrationTest.java` | 覆盖插入/自动填充、查询、乐观锁、逻辑删除、分页 |
| `FlexMultiDataSourceTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/flex/FlexMultiDataSourceTest.java` | 演示 MyBatis-Flex + dynamic-ds 多数据源切换 |
| `MybatisPlusCrudAndFillTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusCrudAndFillTest.java` | 覆盖 CRUD 与自动填充 |
| `MybatisPlusLogicDeleteTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusLogicDeleteTest.java` | 覆盖逻辑删除 |
| `MybatisPlusOptimisticLockTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusOptimisticLockTest.java` | 覆盖乐观锁 |
| `MybatisPlusPaginationTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusPaginationTest.java` | 覆盖分页插件 |
| `DemoServiceCacheTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/cache/DemoServiceCacheTest.java` | 演示 JetCache 两级缓存集成测试 |
| `JasyptEncryptTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/encrypt/JasyptEncryptTest.java` | 演示 Jasypt 配置加密解密测试 |
| `UserCreatedEventFlowTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/event/UserCreatedEventFlowTest.java` | 演示事件桥接端到端测试 |
| `RedissonLockTest` | `frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/redis/RedissonLockTest.java` | 演示 Redisson 分布式锁集成测试 |

### 运行集成测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  mvn -pl frame-me-tester/frame-me-tester-service test
```

仅运行集成测试类：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  mvn -pl frame-me-tester/frame-me-tester-service test -Dtest=DemoMapperIntegrationTest
```

### 技术说明

- 容器使用 **JVM 级别单例**（`static` 块启动），多个测试类共享同一个 MySQL 实例，避免重复拉取/启动。
- `@DynamicPropertySource` 动态覆盖 `spring.datasource.*`，同时禁用 `spring.sql.init.mode=never`，避免与测试自身的 `@Sql` 初始化冲突。
- 每个测试方法标注 `@Transactional`，测试结束后自动回滚，保证隔离性。
- 逻辑删除测试通过 `JdbcTemplate` 直接查数据库验证 `deleted` 字段，绕过 MyBatis-Plus 的自动过滤。
- **Docker 不可用时自动跳过**：`AbstractIntegrationTest` 会在类初始化时检测 Docker 可用性，若不可用则跳过所有测试，避免 CI/本地无 Docker 环境时构建失败。

### 上下文加载测试（H2）

`ApplicationTests` 使用 `application-test.yml` 中配置的 H2 内存数据库，不依赖 Docker，用于快速验证 Spring Boot 上下文可以正常启动。

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
    driver-class-name: org.h2.Driver
```

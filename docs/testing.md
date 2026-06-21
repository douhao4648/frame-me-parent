# 测试与运行

## 测试现状

当前项目中存在的测试位于：

```
frame-me-tester/src/test/java/com/frame/me/tester/ApplicationTests.java
frame-me-tester/src/test/java/com/frame/me/tester/AbstractIntegrationTest.java
frame-me-tester/src/test/java/com/frame/me/tester/mybatis/DemoMapperIntegrationTest.java
frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusCrudAndFillTest.java
frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusLogicDeleteTest.java
frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusOptimisticLockTest.java
frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusPaginationTest.java
```

- `ApplicationTests`：使用 H2 内存数据库验证 Spring Boot 上下文能正常启动，不依赖 Docker。
- `AbstractIntegrationTest`：Testcontainers + MySQL 集成测试基类。
- `DemoMapperIntegrationTest`：覆盖插入/自动填充、查询、乐观锁、逻辑删除、分页。
- `MybatisPlusCrudAndFillTest`：覆盖 CRUD 与自动填充。
- `MybatisPlusLogicDeleteTest`：覆盖逻辑删除行为。
- `MybatisPlusOptimisticLockTest`：覆盖乐观锁版本递增与冲突。
- `MybatisPlusPaginationTest`：覆盖分页插件与条件分页。

其他模块（`frame-me-starter-base`、`frame-me-starter-adapter`、`frame-me-starter-auth`、`frame-me-starter-cloud`）目前没有测试代码。

## 运行测试

### 运行全部测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home mvn test
```

Maven 会按 reactor 顺序编译所有模块，最后执行 `frame-me-tester` 中的测试。

如果本地没有 Docker，`DemoMapperIntegrationTest` 中的测试会自动跳过，`ApplicationTests` 仍会正常执行。

### 运行单个测试类

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester test -Dtest=ApplicationTests
```

### 运行单个测试方法

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester test -Dtest=ApplicationTests#contextLoads
```

### 跳过测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home mvn clean compile -DskipTests
```

## 启动应用

### 通过 Maven 启动

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester spring-boot:run
```

### 运行打包后的 Jar

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester package

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  java -jar frame-me-tester/target/frame-me-tester-1.0.0-SNAPSHOT.jar
```

## 运行时配置

配置文件路径：`frame-me-tester/src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: frame-me-tester
```

- 访问端口：`8080`
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
| `AbstractIntegrationTest` | `frame-me-tester/src/test/java/com/frame/me/tester/AbstractIntegrationTest.java` | 测试基类，负责启动 MySQL 容器并注入数据源配置 |
| `DemoMapperIntegrationTest` | `frame-me-tester/src/test/java/com/frame/me/tester/mybatis/DemoMapperIntegrationTest.java` | 覆盖插入/自动填充、查询、乐观锁、逻辑删除、分页 |
| `MybatisPlusCrudAndFillTest` | `frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusCrudAndFillTest.java` | 覆盖 CRUD 与自动填充 |
| `MybatisPlusLogicDeleteTest` | `frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusLogicDeleteTest.java` | 覆盖逻辑删除 |
| `MybatisPlusOptimisticLockTest` | `frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusOptimisticLockTest.java` | 覆盖乐观锁 |
| `MybatisPlusPaginationTest` | `frame-me-tester/src/test/java/com/frame/me/tester/mybatis/MybatisPlusPaginationTest.java` | 覆盖分页插件 |

### 运行集成测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester test
```

仅运行集成测试类：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester test -Dtest=DemoMapperIntegrationTest
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

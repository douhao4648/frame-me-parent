# 构建与运行

## Java 版本要求

- 项目使用 **Java 21**（根 `pom.xml` 中 `java.version`、`maven.compiler.source`、`maven.compiler.target` 均设置为 `21`）。
- 构建 JDK 需要为 21 或更高版本；编译器以 release/source/target 21 生成兼容 Java 21 的字节码。
- 当前机器已安装 JDK 21 的路径：

```bash
/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
```

建议在 shell 中导出：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
```

## Maven Wrapper

项目已配置 **Maven Wrapper**（根目录 `mvnw` / `mvnw.cmd` + `.mvn/wrapper`），默认绑定 Maven `3.9.9`。本地无需安装 Maven，直接用 `./mvnw`（Windows 用 `mvnw.cmd`）即可构建。

- Wrapper 的 JVM 参数统一放在 `.mvn/jvm.config`（当前配置 `-Dfile.encoding=UTF-8 -Xmx2g`）。
- 如果本地已安装 Maven，也可将以下命令中的 `./mvnw` 替换为 `mvn`，但推荐优先使用 Wrapper，避免版本不一致。
- CI 流水线应统一使用 `./mvnw`，确保与本地构建环境一致。

## 父 POM 关键配置

根 `pom.xml` 路径：`/Users/douhao4648/Documents/Frame_Me/frame-me-parent/pom.xml`

- 父工程：`spring-boot-starter-parent:4.0.7`
- 版本属性：
  - Spring Cloud：`2025.1.2`
  - Spring Cloud Alibaba：`2025.1.0.0`
  - Lombok：`1.18.46`
  - Hutool：`5.8.47`
  - JetCache：`2.8.0.RC`
  - Kryo5：`5.6.2`（由 `frame-me-starter-l1l2-cache` 使用，版本在根 `pom.xml` 集中管理）
- 编译插件：`maven-compiler-plugin:3.15.0`，启用 `-parameters` 参数。
- 注解处理器分层声明：根 POM 的 `annotationProcessorPaths` 全局声明 `lombok` 与 `spring-boot-configuration-processor`（后者为 13 个 starter 生成配置元数据）；`mybatis-flex-processor` 与 `mapstruct-processor` 仅 `frame-me-tester-service` 使用，下放到该模块自行声明（`combine.children="append"` 在继承根配置基础上追加，不覆盖 lombok/configuration-processor）。
- `maven-source-plugin:3.3.1` 会在构建时附带源码包。

## 常用 Maven 命令

### 编译整个工程

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home ./mvnw clean compile
```

### 运行所有测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home ./mvnw test
```

### 运行单个测试类

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./mvnw -pl frame-me-tester/frame-me-tester-service test -Dtest=ApplicationTests
```

### 运行单个测试方法

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./mvnw -pl frame-me-tester/frame-me-tester-service test -Dtest=ApplicationTests#contextLoads
```

### 打包可运行 Jar

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./mvnw -pl frame-me-tester/frame-me-tester-service package
```

### 启动应用

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./mvnw -pl frame-me-tester/frame-me-tester-service spring-boot:run
```

应用默认运行在 `9090` 端口（管理端口 `9091`），应用名称为 `frame-me-tester`。

## Maven Profile

`frame-me-tester/frame-me-tester-service` 中定义了 `p6spy`、`swagger` 两个可选 Maven profile，用于在开发/调试时按需引入额外能力（认证实现不走 profile，jwt / sa-token 通过直接替换依赖切换，详见 [testing.md](./testing.md) 的「认证实现切换」小节）：

### `prometheus` — 指标导出

各可运行服务（`frame-me-tester-service`、`frame-me-sso-service`、`frame-me-audit-service`、`frame-me-gateway`）统一定义了 `prometheus` profile，激活时引入 `micrometer-registry-prometheus`：

```bash
./mvnw -pl <服务模块> spring-boot:run -Pprometheus
```

仅引入 jar 不会自动暴露端点，还需在配置中把 `prometheus` 加入 `management.endpoints.web.exposure.include`（gateway 的 management 端口默认只暴露 `health,offline`）。

### `p6spy` — SQL 监控

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./mvnw -pl frame-me-tester/frame-me-tester-service spring-boot:run -Pp6spy
```

引入 `p6spy-spring-boot-starter`，可在日志中输出实际执行的 SQL 及耗时。需要在 `application.yml` 中开启：

```yaml
decorator:
  datasource:
    enabled: true
    p6spy:
      enable-logging: true
```

p6spy 的 `spy.properties` 配置文件位于 `frame-me-starter-base/src/main/resources/`（历史约定，随 base 打包进 classpath）。`-Pp6spy` 激活时 p6spy 从 classpath 读取该文件，控制日志格式、过滤规则与执行耗时阈值。p6spy 依赖本身由 tester-service 的 `p6spy` profile 引入，base 模块不依赖 p6spy，配置文件仅作为共享默认值随 base 下发。

### `swagger` — 接口文档

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./mvnw -pl frame-me-tester/frame-me-tester-service spring-boot:run -Pswagger
```

引入 `frame-me-starter-doc-openapi`，提供 `/swagger-ui.html` 和 `/v3/api-docs`。需要在 `application.yml` 中开启并配置：

```yaml
me:
  swagger:
    enabled: true
    title: Frame Me API
    description: Frame Me 接口文档
    version: 1.0.0
```

## 如何新增一个子模块

假设新增模块名为 `frame-me-demo`：

1. 在项目根目录创建 `frame-me-demo/` 目录，并新增 `frame-me-demo/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>frame-me-demo</artifactId>
    <name>frame-me-demo</name>
    <dependencies>
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-base</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
</project>
```

2. 在父 `pom.xml` 的 `<modules>` 中添加 `<module>frame-me-demo</module>`。
3. 在父 `pom.xml` 的 `<dependencyManagement>` 中添加：

```xml
<dependency>
    <groupId>com.frame.me</groupId>
    <artifactId>frame-me-demo</artifactId>
    <version>${project.version}</version>
</dependency>
```

**lombok 依赖约定**：根 POM 的 `<dependencyManagement>` 已为 `lombok` 声明 `<scope>provided</scope>`，子模块声明 lombok 时**只需写 `groupId` + `artifactId`，不要重复写 `<scope>`**（继承根 POM 的 provided 即可）。`msg-notify`、`op-audit` 等历史模块重复写了 `<scope>provided</scope>`，语义等价但不统一，属冗余，新模块按本约定省略。

4. 如果模块需要自动注册 Bean，参考 [architecture.md](./architecture.md) 中的自动装配约定，创建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
5. 保持包名为 `com.frame.me.demo.*`，与模块名后缀一致。

## Lint

代码风格 lint 是独立的后续工作，本次架构治理不引入格式化或新的风格规则，也不批量改写现有源码。完整验证使用 Maven Wrapper，模块类别、依赖方向与生产依赖范围按 [architecture-rules.md](./architecture-rules.md) 评审。

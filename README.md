# frame-me-parent

> 个人 Spring Boot 脚手架工程：一套可按需拆分的多模块 Java 框架。

`frame-me-parent` 是一个基于 **Spring Boot 4.0.7 + Java 25** 的多模块 Maven 项目，用于沉淀通用的基础设施、响应规范、异常处理与自动装配约定。项目通过分层模块设计，让外部应用可以选择只引入所需能力，或通过 `frame-me-boot` 聚合模块一键引入全部通用能力。

## 快速开始

项目要求 **JDK 25**，当前机器路径：

```bash
/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home
```

编译全部模块：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home mvn clean compile
```

运行测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home mvn test
```

启动示例应用：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  mvn -pl frame-me-tester spring-boot:run
```

应用默认运行在 `8080` 端口，名称为 `frame-me-tester`。

## 模块概览

| 模块 | 定位 |
|---|---|
| `frame-me-common` | 纯工具模块：`Result<T>`、`ResultCode`、异常体系。 |
| `frame-me-starter-base` | Spring Web 基础设施：全局异常处理、自动装配。 |
| `frame-me-starter-adapter` | 适配层：将内部 `Result<T>` 转换为外部 `Response<T>`。可被外部项目重写。 |
| `frame-me-starter-auth` | 认证授权占位模块。 |
| `frame-me-starter-cloud` | 微服务云组件占位模块。 |
| `frame-me-boot` | 聚合模块，统一引入通用能力（不含 `frame-me-starter-adapter`）。 |
| `frame-me-tester` | Spring Boot 启动入口与验证模块。 |

## 核心约定

- Controller 返回 `Result<T>`，由 `Result2ResponseAdvice` 自动转为 `Response<T>` 给客户端。
- 业务异常抛 `BusinessException`，内部异常抛 `InternalException`。
- 新增模块贡献 Bean 时，使用 `@Configuration(proxyBeanMethods = false)` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。

## License

见 [LICENSE](LICENSE) 文件。

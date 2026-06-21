# frame-me-parent

> 个人 Spring Boot 脚手架工程：一套可按需拆分的多模块 Java 框架。

`frame-me-parent` 是一个基于 **Spring Boot 4.0.7 + Java 25** 的多模块 Maven 项目，用于沉淀通用的基础设施、响应规范、异常处理与自动装配约定。

项目采用 **接口 / Service 分离** 的设计：

- **`frame-me-api`**：纯接口/Interfacer 契约模块，供业务工程的 `xx-api` 模块引用；业务 `xx-api` 之间也可以相互引用。
- **`frame-me-booter`**：聚合启动模块，供业务工程的 `xx-service` 模块引用，一键引入并启动一组通用 starter 能力。

这样，业务工程的 `xx-api` 只依赖接口契约，而 `xx-service` 通过 `frame-me-booter` 统一拉起所有需要的基础设施。

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
  mvn -pl frame-me-tester-service spring-boot:run
```

应用默认运行在 `8080` 端口，名称为 `frame-me-tester`。]

## 模块概览

| 模块 | 定位 |
|---|---|
| `frame-me-api` | 纯接口/Interfacer 契约模块：`IResult<T>`、`ApiConstant`；供业务 `xx-api` 引用。 |
| `frame-me-starter-base` | Spring Web 基础设施：`ResultCode`、异常体系、全局异常处理、自动装配、`IResult<T>` 实现与工厂。 |
| `frame-me-starter-adapter` | 适配层：将内部 `IResult<T>` 转换为外部 `Response<T>`。可被外部项目重写。 |
| `frame-me-starter-dynamic-ds` | 多数据源 starter：基于 baomidou dynamic-datasource，可按 `spring.datasource.*` 自动创建默认 `master` 数据源。 |
| `frame-me-starter-doc-openapi` | 接口文档 starter：基于 SpringDoc OpenAPI，通过 `frame.me.swagger.enabled=true` 开启。 |
| `frame-me-starter-auth` | 认证授权占位模块。 |
| `frame-me-starter-cloud` | 微服务云组件占位模块。 |
| `frame-me-booter` | 聚合启动模块：供业务 `xx-service` 引用，一键拉起通用 starter 能力（含 dynamic-ds，不含 adapter）。 |
| `frame-me-tester` | 测试模块聚合器，包含 `frame-me-tester-api` 与 `frame-me-tester-service`。 |

## 核心约定

- Controller 返回 `IResult<T>`，由 `Result2ResponseAdvice` 自动转为 `Response<T>` 给客户端。
- 业务异常抛 `BusinessException`，内部异常抛 `InternalException`。
- 新增模块贡献 Bean 时，使用 `@Configuration(proxyBeanMethods = false)` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。

## 模块设计约定

在基于 `frame-me-parent` 构建新业务工程时，推荐按以下方式引用本框架：

- **业务 `xx-api` 模块** → 引用 `frame-me-api`
  - 只引入接口契约（如 `IResult<T>`、`ApiConstant`），不引入 Spring starter。
  - 业务 `xx-api` 之间可以相互引用，用于跨业务接口调用。

- **业务 `xx-service` 模块** → 引用 `frame-me-booter`
  - 通过 `frame-me-booter` 一键拉起通用 starter 能力（如 auth、cloud、base 等）。
  - `frame-me-booter` 本身不包含业务代码，只通过传递依赖聚合通用能力。

- **`frame-me-starter-adapter` 不纳入 `frame-me-booter`**
  - 适配层通常需要按项目自定义，因此保持独立，由业务 `xx-service` 按需引入或自行实现。

## License

见 [LICENSE](LICENSE) 文件。

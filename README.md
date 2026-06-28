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
  mvn -pl frame-me-tester/frame-me-tester-service spring-boot:run
```

应用默认运行在 `8080` 端口，名称为 `frame-me-tester`。

## 模块概览

| 模块 | 定位 |
|---|---|
| `frame-me-api` | 纯接口/契约模块：`IResult<T>`、`ApiConstant`；供业务 `xx-api` 引用。 |
| `frame-me-adapter` | 适配层聚合模块（`pom`），含 `frame-me-adapter-api`（老规范契约、分页参数/结果）与 `frame-me-adapter-starter`（`IResult`→`Response` 适配 + 老规范分页工具）。集成 `-starter` 即表示遵循老接口规范。 |
| `frame-me-starter-base` | Spring Web 基础设施：`ResultCode`、异常体系、全局异常处理、`IResult<T>` 实现、MyBatis-Plus。 |
| `frame-me-starter-auth` | 认证授权占位模块。 |
| `frame-me-starter-cloud` | 微服务云组件占位模块。 |
| `frame-me-starter-doc-openapi` | 接口文档 starter：基于 SpringDoc OpenAPI，通过 `me.swagger.enabled=true` 开启。 |
| `frame-me-starter-dynamic-ds` | 多数据源 starter：基于 baomidou dynamic-datasource，按 `spring.datasource.*` 自动创建默认 `master` 数据源。 |
| `frame-me-starter-multi-redis` | Redis 能力 starter：封装 `RedisUtils`（String/Hash/List/Set/ZSet/计数/简单锁，多实例）；引入 Redisson 后自动启用分布式锁、同步原语、Topic、限流。 |
| `frame-me-starter-l1l2-cache` | 两级缓存 starter：基于 JetCache，Caffeine（L1）+ Redis（L2），通过 `me.cache.enabled=true` 开启。 |
| `frame-me-starter-sensi-encrypt` | 配置密钥加密 starter：基于 Jasypt 核心库，启动时解密配置中的 `ME(密文)`，主密码由环境变量注入、不入库。 |
| `frame-me-starter-op-audit` | 审计/行为日志 starter：方法标注 `@AuditLog` 记录动作/参数/返回/异常/耗时，默认打印日志，可经事件桥接发往审计服务。 |
| `frame-me-starter-sse-mvc` | SSE 推送 starter（按需引入）：服务端事件推送，支持按事件类型广播与按接收者定向推送。 |
| `frame-me-starter-ws-mvc` | WebSocket 推送 starter（按需引入）：Servlet 原生 WebSocket 全双工，支持广播与定向推送。 |
| `frame-me-booter` | 聚合启动模块：供业务 `xx-service` 引用，一键拉起通用 starter 能力（含 auth/cloud/dynamic-ds/multi-redis/l1l2-cache/sensi-encrypt/op-audit；不含 adapter、doc-openapi、sse-mvc、ws-mvc）。 |
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

- **`frame-me-adapter` 不纳入 `frame-me-booter`**
  - 适配层通常需要按项目自定义，因此保持独立，由业务 `xx-service` 按需引入 `frame-me-adapter-starter` 或自行实现。`frame-me-starter-doc-openapi` 同样按需引入。

## License

见 [LICENSE](LICENSE) 文件。

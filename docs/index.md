# 项目文档导航

本文档用于帮助 AI 助手（以及开发者）快速理解 `frame-me-parent` 项目的结构、约定与关键实现。

## 项目速览

`frame-me-parent` 是一个基于 **Spring Boot 4.0.7 + Java 25** 的多模块 Maven 脚手架工程，groupId 为 `com.frame.me`。项目采用分层模块设计，下层模块为上层提供基础能力；示例模块 `frame-me-tester` 拆分为 `frame-me-tester-api`（契约）与 `frame-me-tester-service`（实现），最终由 `frame-me-tester-service` 作为可运行的 Spring Boot 入口。

## 阅读前置

- 项目要求 **JDK 25**，当前机器路径为：`/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home`。
- 无 Maven Wrapper，需要系统已安装 `mvn` 并在 PATH 中。
- 运行任何 Maven 命令前建议设置：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home`。

## 文档地图

| 文档 | 回答的问题 |
|---|---|
| [build.md](./build.md) | 如何编译、测试、打包、运行？如何新增子模块？ |
| [architecture.md](./architecture.md) | 模块如何分层？自动装配如何工作？请求响应如何流转？ |
| [event-bridge.md](./event-bridge.md) | 事件桥接如何工作？进程内事件与跨服务事件如何统一？ |
| [conventions.md](./conventions.md) | Result / Response / 异常 / 状态码怎么用？编码风格是什么？ |
| [modules.md](./modules.md) | 每个子模块的职责、依赖、关键类是什么？ |
| [testing.md](./testing.md) | 如何运行测试？如何启动应用？ |
| [reference.md](./reference.md) | 关键类与文件的路径索引、已知扩展点在哪里？ |

## 关键约定一句话

Controller 返回 `IResult<T>`，由 `Result2ResponseAdvice` 转换为 `Response<T>` 返回给客户端；业务异常抛 `BusinessException`，系统异常抛 `InternalException`，统一由 `GlobalExceptionHandler` 处理。

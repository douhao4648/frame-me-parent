# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# 项目身份

`frame-me-parent` 是基于 **Spring Boot 4.0.7 + Java 25** 的多模块 Maven 脚手架，采用 `frame-me-api` / `frame-me-booter` 分离设计，示例模块 `frame-me-tester` 进一步拆分为 `frame-me-tester-api`（契约）与 `frame-me-tester-service`（实现）。详细架构、约定、命令和类索引见 `docs/` 知识库。

# 知识库检索

仓库在 `docs/` 目录下维护了一套项目知识库，`docs/index.md` 是索引入口。在回答实现问题、新增模块、处理异常或配置数据源之前，**优先读取相关文档**，不要仅凭已有记忆推断。

| 文档 | 何时读取 |
|---|---|
| `docs/index.md` | 首次接触项目或需要文档地图时 |
| `docs/build.md` | 构建、测试、打包、新增子模块 |
| `docs/architecture.md` | 模块分层、自动装配、请求/异常流转 |
| `docs/conventions.md` | `Result`/`Response` 用法、异常体系、编码风格、MyBatis-Plus 配置 |
| `docs/modules.md` | 各模块职责、关键类、可配置项 |
| `docs/testing.md` | 测试策略、Testcontainers、启动应用 |
| `docs/reference.md` | 查找关键类路径、已知扩展点 |
| `docs/guides/event-bridge.md` | 专题：事件桥接、进程内事件、跨服务事件 transport |
| `docs/guides/audit.md` | 专题：审计/行为日志、`@AuditLog`、桥接审计服务 |

检索顺序建议：先读 `docs/index.md` 定位主题，再读对应专题文档，必要时结合 `docs/reference.md` 查找具体类路径。

# 最常用命令速查

项目要求 **JDK 25**，当前机器路径：

```bash
/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home
```

```bash
# 编译
mvn clean compile

# 运行全部测试
mvn test

# 运行单个测试类
mvn -pl frame-me-tester/frame-me-tester-service test -Dtest=ApplicationTests

# 启动示例应用
mvn -pl frame-me-tester/frame-me-tester-service spring-boot:run
```

更多命令与 profile（`p6spy`、`swagger`）见 `docs/build.md` 与 `docs/testing.md`。

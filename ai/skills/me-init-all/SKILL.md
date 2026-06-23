---
name: me-init-all
description: 为总工程目录初始化根 CLAUDE.md + docs/ 知识库，并递归为所有子目录初始化子工程知识库
argument-hint: ""
quality: high
---

# me-init-all Skill

## Purpose

为总工程（父工程/聚合工程）目录快速建立一套可复用的根知识库体系，并确保每个子工程都有独立的 `CLAUDE.md` + `docs/`：

- 根目录的 `CLAUDE.md` 作为总工程的 Claude Code 入口指引
- 根目录的 `docs/` 作为总工程知识库，`docs/index.md` 是索引入口
- `docs/projects.md` 维护子工程清单，自动索引各子目录的 `CLAUDE.md` 与 `docs/index.md`
- 对每个还没有 `CLAUDE.md` 的子目录，执行 `me-init` 流程初始化子工程知识库

## When to Activate

用户在总工程/聚合工程目录中输入以下任一内容时触发：

- `/oh-my-claudecode:me-init-all`
- `/me-init-all`
- "初始化总工程知识库"
- "为所有子工程创建 docs"
- "批量初始化项目文档"

## Workflow

1. **确认当前目录为总工程根目录**
   - 项目名：使用当前目录名作为总工程名。
   - 检查当前目录下是否已存在 `CLAUDE.md` 或 `docs/`；若存在，提示用户是否覆盖或合并，不擅自覆盖已有文件。

2. **扫描一级子目录**
   - 列出当前目录下所有一级子目录（排除 `.git`、`.idea`、`.claude`、`.omc`、node_modules 等隐藏或依赖目录）。
   - 对每个子目录：
     - 若子目录已存在 `CLAUDE.md`，记录该子工程信息，跳过初始化。
     - 若子目录不存在 `CLAUDE.md`，进入该子目录并执行 `me-init` 流程（以子目录名作为项目名），创建其 `CLAUDE.md` + `docs/` 知识库。

3. **创建根目录 `CLAUDE.md`**
   - 使用下方模板，填入总工程名、子工程清单、知识库检索指引。

4. **创建根目录 `docs/` 知识库**
   - `docs/index.md`：总工程文档索引入口
   - `docs/projects.md`：子工程清单表，链接到各子工程的 `CLAUDE.md` 与 `docs/index.md`
   - `docs/build.md`：总工程构建约定、批量构建示例、新增子工程步骤
   - `docs/architecture.md`：总工程目录结构、分层原则、跨服务调用约定
   - `docs/conventions.md`：跨子工程响应/异常/状态码、编码风格、数据访问约定
   - `docs/modules.md`：顶层目录职责、子工程模块详情索引
   - `docs/testing.md`：总工程测试策略、批量测试示例
   - `docs/reference.md`：总工程与子工程入口索引

5. **报告结果**
   - 列出本次初始化的子工程列表。
   - 列出根目录创建的文件路径。
   - 提示用户：「请根据实际项目填充 docs/ 中的 TODO 占位内容，并更新 docs/projects.md。」

## Templates

### CLAUDE.md

```markdown
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# 项目身份

`{{PROJECT_NAME}}` 是多服务总工程目录，聚合了后端微服务脚手架、各业务演示/实现服务，以及未来可能加入的前端、Python、Node.js 等二方应用。每个子目录都是一个独立工程，拥有各自的 `CLAUDE.md` 与 `docs/` 知识库。

详细架构、约定、命令和类索引见 `docs/` 知识库。

# 知识库检索

仓库在 `docs/` 目录下维护了一套总工程知识库，`docs/index.md` 是索引入口；各子工程在子目录下维护独立知识库。在回答实现问题、新增模块、处理异常或配置数据源之前，**优先读取相关文档**，不要仅凭已有记忆推断。

| 文档 | 何时读取 |
|---|---|
| `docs/index.md` | 首次接触总工程或需要文档地图时 |
| `docs/projects.md` | 查看子工程清单、进入子工程知识库 |
| `docs/build.md` | 总工程层面的构建、聚合打包、CI/CD 约定 |
| `docs/architecture.md` | 总工程分层、服务边界、跨服务调用约定 |
| `docs/conventions.md` | 跨子工程的编码、接口、命名、数据访问约定 |
| `docs/modules.md` | 总工程内各顶层目录/服务职责 |
| `docs/testing.md` | 总工程测试策略、集成测试 |
| `docs/reference.md` | 关键文件路径、子工程入口、已知扩展点 |

检索顺序建议：
1. 先读 `docs/index.md` 定位主题；
2. 若问题属于某个子工程，按 `docs/projects.md` 进入对应子工程的 `CLAUDE.md` → `docs/index.md`；
3. 跨工程问题回到总工程 `docs/conventions.md` / `docs/architecture.md`。

# 子工程速查

{{SUBPROJECT_TABLE}}

新增子工程后，应更新 `docs/projects.md` 并确保该子工程目录下存在独立的 `CLAUDE.md` + `docs/`。
```

### docs/index.md

```markdown
# 总工程文档导航

本文档用于帮助 AI 助手（以及开发者）快速理解 `{{PROJECT_NAME}}` 总工程的结构、约定与关键实现。

## 项目速览

`{{PROJECT_NAME}}` 是多个微服务/应用工程的总目录，采用「总工程 + 子工程」组织方式：

- 每个子工程在独立子目录中维护，拥有独立的构建、测试、部署生命周期。
- 总工程通过 `docs/projects.md` 统一索引所有子工程，并提供跨工程的约定与架构。
- 当前已集成的子工程：{{SUBPROJECT_LIST}}

## 阅读前置

- 总工程本身不直接编译运行，各子工程独立构建。
- 进入具体业务问题前，先确认问题属于哪个子工程，再阅读该子工程的 `CLAUDE.md` 与 `docs/index.md`。
- 跨子工程的规范（如统一异常、接口响应、数据访问）以总工程 `docs/conventions.md` 为准；若子工程另有约定，优先子工程。

## 文档地图

| 文档 | 回答的问题 |
|---|---|
| [projects.md](./projects.md) | 有哪些子工程？如何进入某个子工程的知识库？ |
| [build.md](./build.md) | 总工程层面的构建、聚合打包、CI/CD 约定是什么？ |
| [architecture.md](./architecture.md) | 总工程如何分层？服务边界与跨服务调用如何约定？ |
| [conventions.md](./conventions.md) | 跨子工程的响应/异常/状态码、编码风格、数据访问约定是什么？ |
| [modules.md](./modules.md) | 总工程内各顶层目录/服务的职责、依赖、关键类是什么？ |
| [testing.md](./testing.md) | 总工程层面的测试策略、集成测试如何运行？ |
| [reference.md](./reference.md) | 关键文件路径、子工程入口、已知扩展点在哪里？ |

## 关键约定一句话

总工程只负责索引与跨工程约定，不替代子工程独立知识库；处理具体问题前，先定位到正确的子工程。
```

### docs/projects.md

```markdown
# 子工程索引

本页列出 `{{PROJECT_NAME}}` 总工程下的所有子工程，并提供各子工程知识库的入口。

## 子工程清单

| 子工程 | 路径 | 独立知识库入口 | 说明 |
|---|---|---|---|
{{SUBPROJECT_ROWS}}

## 如何维护本页

- 新增子工程后，在本表中追加一行。
- 若子工程目录下还没有 `CLAUDE.md` + `docs/`，先运行 `/me-init <子工程名>` 在该目录初始化知识库。
- 删除子工程时，同步删除本表对应行。
```

### docs/build.md

```markdown
# 构建与运行

## 总工程构建原则

`{{PROJECT_NAME}}` 总工程本身不直接编译运行，各子工程拥有独立的构建生命周期。

## 子工程构建入口

{{SUBPROJECT_BUILD_LINKS}}

## 批量构建

```bash
# 示例：进入各子工程后分别构建（根据实际子工程数量调整）
{{BATCH_BUILD_COMMANDS}}
```

## 新增子工程

1. 在总工程目录下新建子目录。
2. 进入子目录并运行 `/me-init <子工程名>` 初始化该子工程的知识库。
3. 更新 `docs/projects.md` 子工程清单。
```

### docs/architecture.md

```markdown
# 架构设计

## 总工程结构

{{ARCHITECTURE_TREE}}

## 分层原则

- 每个子工程独立演进，独立构建、测试、部署。
- 子工程之间通过接口契约、事件或 RPC 协作，不直接依赖彼此内部实现。
- 总工程提供跨工程约定索引，不承载业务代码。

## 跨服务调用约定

TODO：补充统一的服务发现、接口鉴权、链路追踪、异常透传等约定。
```

### docs/conventions.md

```markdown
# 编码约定

## 适用范围

本页约定适用于 `{{PROJECT_NAME}}` 总工程下所有子工程。若子工程 `docs/conventions.md` 另有说明，优先以子工程为准。

## 响应规范

TODO：补充总工程统一的接口响应结构（如 `Response<T>` / `IResult<T>`）。

## 异常体系

TODO：补充总工程统一的异常基类与全局处理约定。

## 编码风格

- 各子工程优先遵循自身技术栈的主流风格。
- 跨工程公共模块命名、包结构应保持一致。

## 数据访问约定

TODO：补充跨工程的数据库命名、表前缀、分库分表、数据源路由等约定。

## 子工程独立约定

{{SUBPROJECT_CONVENTION_LINKS}}
```

### docs/modules.md

```markdown
# 模块速查

## 顶层目录说明

| 目录 | 职责 |
|---|---|
| `docs/` | 总工程知识库与跨工程约定 |
{{TOP_LEVEL_DIRS}}

## 子工程模块详情

{{SUBPROJECT_MODULE_LINKS}}
```

### docs/testing.md

```markdown
# 测试与运行

## 测试策略

- 各子工程独立负责单元测试与集成测试。
- 总工程层面可补充跨服务集成测试或端到端测试（如有）。

## 子工程测试入口

{{SUBPROJECT_TESTING_LINKS}}

## 批量测试

```bash
# 示例：批量运行各子工程测试（根据实际子工程数量调整）
{{BATCH_TEST_COMMANDS}}
```
```

### docs/reference.md

```markdown
# 关键文件索引

## 总工程入口

| 文件 | 路径 | 作用 |
|---|---|---|
| 总工程入口 | [../CLAUDE.md](../CLAUDE.md) | 总工程 Claude Code 指引 |
| 文档索引 | [./index.md](./index.md) | 总工程知识库入口 |
| 子工程清单 | [./projects.md](./projects.md) | 子工程索引与跳转 |

## 子工程入口

| 子工程 | CLAUDE.md | docs 索引 |
|---|---|---|
{{SUBPROJECT_REFERENCE_ROWS}}

## 已知扩展点

- 新增子工程时，同步更新 `docs/projects.md` 与本页。
```

## Notes

- 该 skill 生成的是**知识库骨架**，创建后必须根据真实项目填充 `{{...}}` 占位内容。
- 不要覆盖用户已存在的文档，除非用户明确说「覆盖」。
- 生成完成后，主动提示用户检查并补全 `docs/conventions.md` 与 `docs/architecture.md` 中的项目特定约定。

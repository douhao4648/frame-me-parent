---
name: me-init
description: 为新工程初始化 CLAUDE.md + docs/ 知识库体系，以 CLAUDE.md 为入口索引到 docs 目录的规范文档
triggers:
  - me-init
  - init-project
  - project-scaffold
  - create-knowledge-base
argument-hint: "[project-name]"
quality: high
---

# me-init Skill

## Purpose

为新工程快速建立一套可复用的知识库体系：

- 仓库根目录的 `CLAUDE.md` 作为 Claude Code 的入口指引
- `docs/` 目录作为项目知识库，`docs/index.md` 作为索引入口
- 后续所有 Claude Code 实例在开发前，优先按 `CLAUDE.md` → `docs/index.md` → 专题文档 的顺序检索

## When to Activate

用户在新仓库中输入以下任一内容时触发：

- `/oh-my-claudecode:me-init`
- `/me-init`
- "帮我初始化知识库"
- "创建 docs 知识库"
- "新建项目文档体系"

## Workflow

1. **确定项目基本信息**
   - 项目名：优先使用用户传入的 `[project-name]`；未传入时取当前目录名。
   - 检测项目类型：读取根目录的 `pom.xml`、`build.gradle`、`package.json`、`pyproject.toml`、`go.mod`、`Cargo.toml` 等文件，判断技术栈。
   - 若无法判断，在生成文档时使用通用占位符，由用户后续补充。

2. **检查是否已存在**
   - 如果根目录已存在 `CLAUDE.md`，提示用户是否覆盖或合并。
   - 如果 `docs/` 已存在部分文件，仅创建缺失文件，不覆盖已有文件（除非用户明确要求）。

3. **创建 `CLAUDE.md`**
   - 使用下方模板，填入项目名、技术栈、主要命令。
   - 保持简洁：项目身份 + 知识库检索指引 + 最常用命令速查。

4. **创建 `docs/` 知识库**
   - `docs/index.md`：索引入口与文档地图
   - `docs/build.md`：构建、测试、打包、运行、新增子模块/组件
   - `docs/architecture.md`：模块/组件分层、依赖方向、自动装配/初始化机制、请求响应/异常流转
   - `docs/conventions.md`：响应规范、异常体系、编码风格、数据访问约定
   - `docs/modules.md`：每个模块/组件的职责、依赖、关键类
   - `docs/testing.md`：测试策略、运行测试、启动应用、示例接口
   - `docs/reference.md`：关键文件索引、类速查、已知扩展点

5. **报告结果**
   - 列出创建的文件路径。
   - 提示用户：「请根据实际项目填充 docs/ 中的 TODO 占位内容。」

## Detection Rules

| 文件存在 | 技术栈 | 构建/运行命令示例 |
|---|---|---|
| `pom.xml` | Java + Maven | `mvn clean compile`, `mvn test`, `mvn spring-boot:run` |
| `build.gradle` | Java + Gradle | `./gradlew build`, `./gradlew test`, `./gradlew bootRun` |
| `package.json` | Node.js / Frontend | `npm install`, `npm test`, `npm run dev` |
| `pyproject.toml` | Python | `pip install -e .`, `pytest`, `python -m app` |
| `go.mod` | Go | `go build`, `go test`, `go run ./cmd/app` |
| `Cargo.toml` | Rust | `cargo build`, `cargo test`, `cargo run` |
| 以上均无 | 通用 | 保留占位符，由用户补充 |

## Templates

### CLAUDE.md

```markdown
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# 项目身份

`{{PROJECT_NAME}}` 是 {{PROJECT_DESCRIPTION}}。

详细架构、约定、命令和类索引见 `docs/` 知识库。

# 知识库检索

仓库在 `docs/` 目录下维护了一套项目知识库，`docs/index.md` 是索引入口。在回答实现问题、新增模块、处理异常或配置数据源之前，**优先读取相关文档**，不要仅凭已有记忆推断。

| 文档 | 何时读取 |
|---|---|
| `docs/index.md` | 首次接触项目或需要文档地图时 |
| `docs/build.md` | 构建、测试、打包、运行、新增子模块/组件 |
| `docs/architecture.md` | 模块/组件分层、依赖方向、初始化机制、请求/异常流转 |
| `docs/conventions.md` | 响应/异常规范、编码风格、数据访问约定 |
| `docs/modules.md` | 各模块/组件职责、关键类、可配置项 |
| `docs/testing.md` | 测试策略、运行测试、启动应用 |
| `docs/reference.md` | 查找关键文件路径、类索引、已知扩展点 |

检索顺序建议：先读 `docs/index.md` 定位主题，再读对应专题文档，必要时结合 `docs/reference.md` 查找具体类路径。

# 最常用命令速查

```bash
{{QUICK_COMMANDS}}
```

更多命令与配置见 `docs/build.md` 与 `docs/testing.md`。
```

### docs/index.md

```markdown
# 项目文档导航

本文档用于帮助 AI 助手（以及开发者）快速理解 `{{PROJECT_NAME}}` 项目的结构、约定与关键实现。

## 项目速览

{{PROJECT_OVERVIEW}}

## 阅读前置

{{PREREQUISITES}}

## 文档地图

| 文档 | 回答的问题 |
|---|---|
| [build.md](./build.md) | 如何编译、测试、打包、运行？如何新增子模块/组件？ |
| [architecture.md](./architecture.md) | 模块/组件如何分层？初始化/装配如何工作？请求响应如何流转？ |
| [conventions.md](./conventions.md) | 响应/异常/状态码怎么用？编码风格是什么？数据访问有什么约定？ |
| [modules.md](./modules.md) | 每个子模块/组件的职责、依赖、关键类是什么？ |
| [testing.md](./testing.md) | 如何运行测试？如何启动应用？ |
| [reference.md](./reference.md) | 关键类与文件的路径索引、已知扩展点在哪里？ |

## 关键约定一句话

{{ONE_LINE_CONVENTION}}
```

### docs/build.md

```markdown
# 构建与运行

## 环境要求

{{ENV_REQUIREMENTS}}

## 常用命令

### 编译/安装依赖

```bash
{{BUILD_COMMAND}}
```

### 运行测试

```bash
{{TEST_COMMAND}}
```

### 运行单个测试

```bash
{{SINGLE_TEST_COMMAND}}
```

### 启动应用

```bash
{{RUN_COMMAND}}
```

## 新增子模块/组件

{{HOW_TO_ADD_MODULE}}

## Lint / 代码检查

{{LINT_COMMAND}}
```

### docs/architecture.md

```markdown
# 架构设计

## 模块/组件依赖图

{{ARCHITECTURE_DIAGRAM}}

更准确的依赖关系：

{{DEPENDENCY_DESCRIPTION}}

## 分层原则

{{LAYERING_PRINCIPLES}}

## 初始化 / 自动装配机制

{{INIT_MECHANISM}}

## 响应与异常流水线

### 正常请求

{{NORMAL_FLOW}}

### 异常请求

{{EXCEPTION_FLOW}}

### 关键类路径

{{KEY_CLASSES}}

## 扩展提示

{{EXTENSION_POINTS}}
```

### docs/conventions.md

```markdown
# 编码约定

## 响应规范

{{RESPONSE_CONVENTION}}

## 异常体系

{{EXCEPTION_CONVENTION}}

## 状态码

{{STATUS_CODES}}

## 编码风格

{{CODING_STYLE}}

## 数据访问约定

{{DATA_ACCESS_CONVENTION}}

## 接口/文档约定

{{API_DOC_CONVENTION}}
```

### docs/modules.md

```markdown
# 模块速查

## 模块依赖速查表

| 模块/组件 | 直接依赖 | 职责 |
|---|---|---|
{{MODULE_TABLE}}

## 详细说明

{{MODULE_DETAILS}}
```

### docs/testing.md

```markdown
# 测试与运行

## 测试现状

{{TESTING_STATUS}}

## 运行测试

```bash
{{TEST_COMMANDS}}
```

## 启动应用

```bash
{{RUN_COMMANDS}}
```

## 测试约定

{{TESTING_CONVENTIONS}}
```

### docs/reference.md

```markdown
# 关键文件索引

## 项目配置

| 文件 | 路径 |
|---|---|
{{CONFIG_FILES}}

## 核心类/文件

| 类/文件 | 路径 | 作用 |
|---|---|---|
{{CORE_FILES}}

## 已知扩展点

{{KNOWN_EXTENSION_POINTS}}
```

## Notes

- 该 skill 生成的是**知识库骨架**，创建后必须根据真实项目填充 `{{...}}` 占位内容。
- 不要覆盖用户已存在的文档，除非用户明确说「覆盖」。
- 生成完成后，主动提示用户检查并补全 `docs/conventions.md` 与 `docs/architecture.md` 中的项目特定约定。
- 若项目类型无法识别，使用通用模板并保留占位符。

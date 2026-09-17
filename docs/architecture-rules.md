# 架构治理规则

本文档是 `frame-me-parent` 的维护中模块边界政策，与根 `pom.xml` 的 Maven 模型、实际 reactor 共同构成规则的权威来源；模块边界与依赖方向由评审按本文档矩阵核对；历史设计记录不覆盖这里的当前规则。

## 当前构建基线

- Java 编译基线是 **21**：根 POM 的 `java.version`、`maven.compiler.source` 与 `maven.compiler.target` 均为 `21`。构建 JDK 可以是更高版本，但不得把编译目标升级到更高版本。
- Spring Boot 父版本是 **4.0.7**，Spring Cloud 是 **2025.1.2**，Spring Cloud Alibaba 是 **2025.1.0.0**。
- Hutool 版本是 **5.8.47**。
- Maven Wrapper 的基线是 **3.9.9**。开发和 CI 均使用 `./mvnw`。
- `frame-me-gateway` 是已实现的 Spring Cloud Gateway WebFlux 网关，不是占位工程；它保持 WebFlux 与 Servlet starter 隔离。
- `frame-me-boot` 是可扩展的 starter 聚合 preset。当前 POM 聚合 auth、multi-redis、l1l2-cache、sensi-encrypt、op-audit、msg-notify；该列表不是冻结清单，治理只约束 boot 只能依赖 starter/client。

本次治理不引入代码风格 lint 或批量格式化；style lint 是独立的后续工作。模块边界由评审按本文档矩阵核对。

## 模块分类矩阵

每个 reactor 项目必须且只能命中一个类别。分类器按下表自上而下判断；`artifactId` 是项目的 Maven artifactId，`packaging` 缺省时按 Maven 默认的 `jar` 处理。新 artifact 如果不匹配任何行，必须失败并给出 artifactId 与 POM 路径。

| 优先级 | 匹配条件 | 类别 | 语义 |
| --- | --- | --- | --- |
| 1 | `packaging` 为 `pom` | aggregator | 仅聚合子模块，不向生产依赖图输出边。 |
| 2 | artifactId 恰为 `frame-me-api` | foundation API | 框架最底层 API/基础契约。 |
| 3 | artifactId 以 `-contract` 结尾 | shared contract | provider-neutral 的共享契约；只可依赖 foundation API。 |
| 4 | artifactId 以 `-api` 结尾 | domain API | 某一提供方拥有的业务 API；不可跨域依赖其他 domain API。 |
| 5 | artifactId 以 `-service` 结尾 | service | 业务实现或可运行服务。 |
| 6 | artifactId 包含 `-starter` | starter/client | 自动装配 starter 或提供方 client starter。 |
| 7 | artifactId 恰为 `frame-me-boot` | boot preset | 可扩展的 starter 聚合入口。 |
| 8 | artifactId 恰为 `frame-me-gateway` | gateway | WebFlux 网关入口，只有显式网关安全 allowlist。 |
| 9 | artifactId 恰为 `frame-me-aliyun-common` | common library | 不带自动装配的公共库底座。 |

`frame-me-api`、`frame-me-boot`、`frame-me-gateway` 与 `frame-me-aliyun-common` 的精确匹配用于避免后缀规则误分类。若未来一个 artifact 同时满足多个非聚合条件，必须先调整本表，而不是默默选择类别。

## 依赖计数与范围矩阵

评审依赖方向时，只读取每个 reactor POM 的直接 `<dependencies>`，构造以 source artifact 为起点、target artifact 为终点的有向图。只有同时满足以下条件的依赖才计入：

- `groupId` 是 `com.frame.me`，并且 target 是当前 reactor 中的项目；
- 依赖是直接声明，包含 `optional=true` 的直接依赖；
- scope 属于生产范围（缺省 scope 按 `compile` 处理）。

| POM 关系/依赖范围 | 是否计入直接边 | 说明 |
| --- | --- | --- |
| `compile`（或未写 scope） | 是 | 默认生产类路径。 |
| `provided` | 是 | 仍是生产 API/编译边界，不能绕过规则。 |
| `runtime` | 是 | 运行时生产边界，不能绕过规则。 |
| `optional` + `compile`/`provided`/`runtime` | 是 | optional 不会豁免架构边界。 |
| `test` | 否 | 测试专用依赖不参与生产架构图。 |
| `dependencyManagement` 中的声明 | 否 | 只有 `<dependencies>` 的直接声明才形成边。 |
| BOM import（`type=pom` + `scope=import`） | 否 | 版本管理关系不是模块依赖。 |
| `<parent>`、`<modules>` 聚合关系 | 否 | Maven 继承/聚合关系不是生产依赖边。 |
| 注释中的坐标 | 否 | 注释不参与模型。 |
| 外部坐标或非 `com.frame.me` 坐标 | 否 | 只校验本 reactor 的内部边。 |

所有计数边都纳入全图环检查；任何有向环都视为违规，即使环上的每条单边分别在类别矩阵中允许。

## 允许的出边矩阵

下表定义 source 类别允许依赖的 target 类别。未列出的 target 一律禁止；因此禁止依赖 `service` 或 `gateway` 的类别没有隐含的消费者例外。

| source 类别 | 允许的 target 类别 | 边界说明 |
| --- | --- | --- |
| aggregator | 无 | 聚合器只通过 `<modules>` 组织项目。 |
| foundation API | 无 | 基础 API 保持无内部模块依赖。 |
| shared contract | foundation API | 只放 provider-neutral 模型/契约。 |
| domain API | foundation API、shared contract | API 由提供方拥有，不连接其他业务 domain API。 |
| service | foundation API、shared contract、domain API、starter/client、boot preset、common library | 消费者 service 可依赖提供方 API 或提供方 client starter；不得依赖 service 或 gateway。 |
| starter/client | foundation API、shared contract、domain API、starter/client、common library | client starter 可以封装提供方 API，但不得依赖 service、boot 或 gateway。 |
| boot preset | starter/client | boot 只聚合 starter；具体 starter 清单可扩展，不固定为当前六项。 |
| common library | foundation API、shared contract、common library | 公共库保持无服务运行时依赖。 |
| gateway | 仅 `frame-me-starter-cloud`、`frame-me-starter-sensi-encrypt`、`frame-me-starter-cloud-nacos` | 这是当前显式 gateway-safe allowlist；不允许 boot、base、auth、multi-redis 或其他内部运行时模块。 |

特别约束：不存在任何类别到 `service` 或 `gateway` 的允许出边；所有已计数的内部边仍受全局环检测约束。跨域 API-to-API、service-to-service、starter-to-service、starter-to-boot、starter-to-gateway、boot-to-non-starter 以及 gateway allowlist 之外的内部边都必须报告 source、target、违反的规则和 source POM 路径。

## 提供方 API 与 client starter

API 契约属于提供方。跨域调用的方向是“消费者 service → 提供方 `*-api` 或提供方 `*-starter`/client starter”，而不是让两个业务 API 互相引用。需要共享且 provider-neutral 的模型时，才新增有真实内容的 `*-contract` 模块；不得为了满足命名规则创建空 contract 模块。

## 异常元数据契约

架构或依赖收敛异常不得静默放行，也不得永久存在。每条异常都必须在本文件的异常表（或同等机器可读记录）中填写以下全部字段：

| 字段 | 要求 |
| --- | --- |
| `rule` | 被例外的规则标识，例如 `DEPENDENCY_DIRECTION` 或 `DEPENDENCY_CONVERGENCE`。 |
| `edge/coordinate` | 精确的 source → target 边，或精确的 groupId:artifactId:version 坐标；禁止整组 wildcard。 |
| `rationale` | 为什么当前不能按规则修复，以及影响范围。 |
| `owner` | 负责移除异常的团队或个人。 |
| `expiry/removal condition` | 明确的到期日期或可验证的移除条件；不得写“长期”“以后再处理”。 |

当前没有已登记的架构或依赖收敛异常；异常表保持为空，未来新增异常时必须按上述字段新增一行。

若以后出现安全扫描抑制，还必须包含 finding 的 `notes`、owner 与有界的 `until` 日期。真实漏洞不能以抑制代替升级或修复。

## API 兼容性门禁（延后启用）

当前版本是 `1.0.0-SNAPSHOT`，没有可比较的非 SNAPSHOT 公共 API 基线，因此现在不启用 Revapi/japicmp 或兼容性失败门禁。第一批非 SNAPSHOT 公共 API artifact 出现在配置的 artifact repository 后，在接受下一次 release 之前新增并启用 `-Papi-compat` profile；目标包括 `frame-me-api`、adapter API、SSO API、audit API 以及未来发布的 API，排除 tester、service 与 launcher 模块。该 profile 只做发布基线比较，不能替代依赖方向评审。

## 权威来源与变更

根 POM、当前 reactor 的 Maven 模型与本文档共同组成当前治理面。修改模块命名或依赖方向时，先更新本策略，再修改 POM；不通过重写历史计划来改变当前规则。

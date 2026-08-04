# Nacos 配置中心 + 注册中心集成设计

> 日期：2026-08-04
> 状态：设计完成，待用户复核后转入实现计划

## 背景与目标

`frame-me-parent` 是 Spring Boot 4.0.7 + Java 25 + Spring Cloud 2025.1.2 的多模块 Maven 脚手架。根 pom 已声明 `spring-cloud-alibaba-dependencies:2025.1.0.0` BOM 与 `spring-cloud-dependencies:2025.1.2` BOM（`docs/reference.md` 第 383-385 行），但尚未在任何模块引入具体 starter。`frame-me-starter-cloud` 当前为占位模块（仅 `CloudConstant` 空壳）。

本设计引入 Nacos 作为配置中心 + 注册中心，同时填充 `frame-me-starter-cloud` 为云基础底座，并补全 `sensi-encrypt` 的运行时刷新解密链路。

### 兼容性（已核实）

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Cloud Alibaba | 2025.1.0.0 | 根 pom BOM 已声明，支持 Spring Boot 4.0.x + Spring Cloud 2025.1.x |
| nacos-client | 3.1.1 | SCA BOM 内置，不手动覆盖 |
| JDK 基线 | 17+ | Java 25 向前兼容 |

依赖坐标（版本由 BOM 管控，子模块不写 version）：
- `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config`
- `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery`
- `org.springframework.cloud:spring-cloud-commons`

## 总体架构

```
frame-me-starter-cloud              ← 云基础底座
  ├── pom: spring-cloud-commons（含 context: @RefreshScope / RefreshEvent / ContextRefresher / LoadBalancerClient）
  ├── pom: frame-me-starter-sensi-encrypt (optional)
  ├── RefreshDecryptListener          ← 配置中心无关的刷新解密（监听 EnvironmentChangeEvent）
  └── CloudAutoConfiguration         ← 装配监听器

frame-me-starter-cloud-nacos         ← Nacos 具体组件（极薄）
  ├── pom: nacos-config + nacos-discovery starter
  ├── pom: frame-me-starter-cloud（继承刷新解密能力）
  └── NacosCloudConstant             ← 占位常量

frame-me-starter-sensi-encrypt       ← 解密能力提供方（核心逻辑零改动）
  └── DecryptedPropertySource        ← 改 public（class + 构造器），供 cloud 复用
```

### 设计原则

1. **不造 `me.cloud.nacos.*` 配置前缀**：业务配置全走 SCA 原生 `spring.cloud.nacos.*`，`me.*` 只承载项目独有的约定（本设计中无新增 `me.*` 配置项）。
2. **不纳入 `frame-me-boot`**：cloud 与 cloud-nacos 均由业务 `xx-service` 显式引入，与 mybatis-plus / dynamic-ds 等约定一致。
3. **配置中心无关的解密抽象放 cloud**：刷新解密不绑定 Nacos，未来接 Apollo 等其他配置中心零成本。
4. **ME(密文) 只做启动期 + 运行时刷新解密，不支持敏感值热更新生效**：敏感值变更走重启，符合"敏感配置低频变更"的现实。

## 模块详细设计

### 1. `frame-me-starter-cloud`（云基础底座）

**定位变更**：从占位模块变为云基础底座，承载所有云组件共享的基础能力。

**pom 依赖**：
```xml
<dependencies>
    <dependency>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-starter-base</artifactId>
    </dependency>
    <!-- 云基础底座：配置刷新体系 + LB 抽象 -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-commons</artifactId>
    </dependency>
    <!-- 刷新解密能力：optional，消费方同时引入 sensi-encrypt 且配主密码时才装配 -->
    <dependency>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-starter-sensi-encrypt</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

**关键类**：

- `com.frame.me.cloud.config.CloudAutoConfiguration` — 自动装配入口，注册刷新解密监听器。
- `com.frame.me.cloud.config.RefreshDecryptListener` — `ApplicationListener<EnvironmentChangeEvent>`，配置中心无关的刷新解密。
- `com.frame.me.cloud.CloudConstant` — 保留现有占位常量类。

**`RefreshDecryptListener` 逻辑**：

```
收到 EnvironmentChangeEvent
  → 遍历 environment.getPropertySources()
  → 对每个 EnumerablePropertySource：
      - 已是 DecryptedPropertySource → 跳过（防重复包装）
      - 含 ME(密文) → 原位 replace 成 DecryptedPropertySource（复用 sensi-encrypt 的类）
  → 解密（复用注入的 StringEncryptor）
```

**装配条件**（遵循 `docs/conventions.md` 模式 A：`@ConditionalOnClass` + 隔离内部配置类）：
- `@ConditionalOnClass({EnumerablePropertySource.class, DecryptedPropertySource.class})` — sensi-encrypt 缺席时整个解密配置类退避
- `@ConditionalOnBean(StringEncryptor.class)` — 未配主密码（无 `StringEncryptor` Bean）时不装配

**装配时机**：`StringEncryptor` Bean 由 sensi-encrypt 的 `EncryptAutoConfiguration`（`@ConditionalOnProperty("me.encrypt.password")`）在自动装配阶段注册，早于运行时 refresh，监听器能正常装配。无时序问题。

**边界**：用户未配 `me.encrypt.password` 时，`StringEncryptor` Bean 不存在，监听器不装配，`ME(密文)` 不被解密——合理行为（未启用加密则无需解密）。

**自动装配注册**：通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `CloudAutoConfiguration`。

### 2. `frame-me-starter-cloud-nacos`（Nacos 具体组件）

**定位**：引入 SCA Nacos starter，提供配置中心 + 注册中心能力。极薄，不写解密逻辑（从 cloud 继承）。

**pom 依赖**：
```xml
<dependencies>
    <dependency>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-starter-cloud</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

**关键类**：
- `com.frame.me.cloud.nacos.NacosCloudConstant` — 占位常量类（遵循 `*Constant` 约定，`final` + 私有构造器）。

**不写自动装配类**：SCA 官方 starter 自带装配（`NacosConfigAutoConfiguration` / `NacosDiscoveryAutoConfiguration`），不重写。模块几乎只剩 pom。

**粒度约定（先合后拆）**：当前一个模块同时提供 config + discovery，包内不强制隔离（SCA 自身的包结构已隔离）。未来如需拆分，改为 `frame-me-starter-cloud-nacos-config` 与 `frame-me-starter-cloud-nacos-discovery` 两个模块，依赖路径调整即可。

### 3. `frame-me-starter-sensi-encrypt`（解密能力提供方）

**改动**：仅访问权限调整，核心解密逻辑零改动。

- `com.frame.me.encrypt.env.DecryptedPropertySource`：`class` → `public class`，构造器 `DecryptedPropertySource(...)` → `public`。
- `EncryptablePropertyEnvironmentPostProcessor`、`DecryptedPropertySource` 的解密逻辑、缓存机制**全部不变**。

**类型适配点（实现时定）**：`DecryptedPropertySource` 构造器接收 `StandardPBEStringEncryptor`，但 `EncryptAutoConfiguration` 暴露的 Bean 类型是 `StringEncryptor` 接口。cloud 的 `RefreshDecryptListener` 注入后需要处理类型——两种方案，编码时择一：
- cloud 注入 `StringEncryptor`，运行时判断/强转为 `StandardPBEStringEncryptor`（jjwt 的 `JasyptEncryptor.create` 返回的就是 `StandardPBEStringEncryptor`，类型匹配）
- 或把 `DecryptedPropertySource` 构造器参数改为 `StringEncryptor` 接口（sensi-encrypt 内部也用接口，更解耦）

推荐方案二（接口化），改动更干净，但属于实现细节，不阻塞设计。

## 配置约定

**全走 SCA 原生 `spring.cloud.nacos.*`，不造 `me.cloud.nacos.*`**：

```yaml
spring:
  profiles:
    active: dev
  cloud:
    nacos:
      server-addr: nacos.example.com:8848
      username: nacos                              # 连 Nacos 的凭证，写本地 application.yml
      password: ME(GXXXX)                          # 可用 ME(密文)，sensi-encrypt 启动期解密
      namespace: ${spring.profiles.active}         # namespace=环境，原生占位符实现
      config:
        file-extension: yaml
        # refresh-enabled: true                    # 默认 true，业务配置热刷新
      discovery:
        cluster-name: BJ
```

### 多环境隔离约定

`namespace = ${spring.profiles.active}`：dev profile → dev namespace，生产 namespace 天然隔离。不写映射表、不写总开关，全用 SCA 原生占位符。

### 敏感值分类处理

| 敏感值类型 | 存放位置 | 解密时机 | 改动 |
|---|---|---|---|
| 连 Nacos 的凭证（`spring.cloud.nacos.username/password`） | 本地 `application.yml` | sensi-encrypt 启动期（`EnvironmentPostProcessor` 阶段，Nacos 源走 `spring.config.import` 在 sensi-encrypt 之前注入，已就位） | 零改动 |
| 业务敏感值（`spring.datasource.password` 等） | Nacos 远程配置写成 `ME(密文)` | 启动期（sensi-encrypt 扫到 Nacos 源）+ 运行时刷新（cloud 的 `RefreshDecryptListener`） | cloud 加监听器 |

### ME 解密链路时序（已核实）

**启动期（零改动）**：
```
ConfigDataEnvironmentPostProcessor (order = HIGHEST_PRECEDENCE + 10 = -2147483638)
  → 触发 spring.config.import → NacosConfigDataLoader 拉配置
  → NacosPropertySource 注入 environment（继承 MapPropertySource → EnumerablePropertySource）
EncryptablePropertyEnvironmentPostProcessor (order = LOWEST_PRECEDENCE = +2147483647)
  → 此时 Nacos 源已就位
  → 遍历所有 EnumerablePropertySource（含 Nacos 源）
  → 原位包装成 DecryptedPropertySource
  → 全量预解密（fail-fast）
```

`EnvironmentPostProcessor` 按 order 升序执行，`ConfigDataEnvironmentPostProcessor`（`-2147483638`）远早于 sensi-encrypt（`+2147483647`），Nacos 源在 sensi-encrypt 跑时已存在。**启动期不需要额外监听器/PostProcessor。**

**运行时刷新（cloud 加监听器）**：
```
Nacos server 推送配置变更（Data ID 级，整个配置文件全量推送）
  → NacosContextRefresher 收到 → 发布 RefreshEvent
  → RefreshEventListener 监听 → ContextRefresher.refreshEnvironment()
  → 重新加载 ConfigData → 构造全新 NacosPropertySource 替换旧源
  → 旧 DecryptedPropertySource（含其 decryptedCache 实例字段）被移出 environment，GC 回收
  → 发布 EnvironmentChangeEvent
  → cloud 的 RefreshDecryptListener 收到
  → 遍历 environment，对新加入的未包装含密文源重新包装成 DecryptedPropertySource
```

**刷新粒度**：Nacos 推送粒度是 Data ID（整个配置文件）级，不是字段级。Nacos server 推送整个 Data ID 的全量内容，客户端构造全新 `NacosPropertySource` 整体替换。哪怕只改一个字符，整个源都会被重建替换。

**缓存不累积**：`DecryptedPropertySource.decryptedCache` 是实例字段（`ConcurrentHashMap`），生命周期绑定在属性源实例上。refresh 时整个实例被丢弃、新建——缓存随之清零重建，不跨 refresh 累积。驻留极限 = 单个实例生命周期内的密文条目数（通常个位数）。

**短窗口期**：`EnvironmentChangeEvent` 触发到监听器跑完之间，environment 里的密文短暂裸露。期间 `@RefreshScope` bean 重建若读密文会踩坑——但 `@RefreshScope` 一般不用在敏感配置 bean 上，可接受。

## 服务间调用

- Nacos discovery 自动注册服务实例
- Spring Cloud LoadBalancer（SCA discovery 自带，基于 spring-cloud-commons 的 `LoadBalancerClient`）做服务名解析
- base 的 `PoolingRestClientAutoConfiguration` 提供池化 `RestClient.Builder`，LB 接管后 `RestClient` 调用 `http://service-name/...` 自动走 LB 选实例
- **不引 OpenFeign**：现有 HTTP Interface（`@HttpExchange`）+ RestClient 够用，调用量上来再引

### 与 auth 头传播的衔接（零改动）

`frame-me-starter-auth` 已实现 `me.auth.propagate` 认证头传播，其中 `service-discovery.enabled=true` 时用 `LoadBalancerClient.choose(host)` 判定服务名调用（`docs/conventions.md` 第 649 行），自动放行内部服务间认证头传播。Nacos discovery 落地后，cloud 显式声明 `spring-cloud-commons`，`LoadBalancerClient` 直接可用，**auth 模块零改动**天然衔接。

## 测试策略

### 1. `frame-me-starter-cloud` 测试

- **`RefreshDecryptListenerTest`**：用 `ApplicationContextRunner` 切片测试
  - 模拟 `EnvironmentChangeEvent`，验证含 `ME(密文)` 的属性源被包装成 `DecryptedPropertySource`
  - 验证已包装的源不被重复包装（`instanceof` 判断）
  - 验证未配主密码（无 `StringEncryptor` Bean）时监听器不装配
- **`CloudAbsentClasspathTest`**：用 `FilteredClassLoader` 屏蔽 sensi-encrypt，验证缺类时整体退避不抛异常（遵循 `docs/conventions.md` 第 156 行的 AbsentClasspath 测试约定）

### 2. `frame-me-starter-cloud-nacos` 测试

- **`NacosCloudAbsentClasspathTest`**：屏蔽 nacos starter 类，验证模块缺类时退避
- **集成测试（可选，放 tester-service）**：用 Testcontainers 起 Nacos Server，验证：
  - 服务注册到 Nacos
  - 配置从 Nacos 拉取
  - `ME(密文)` 配置在启动期解密
  - Nacos 配置变更触发刷新后，`ME(密文)` 被重新解密
  - 测试用 `Assumptions.assumeTrue` 或 `@EnabledIf` 守卫 Docker 可用性（参考 tester-service 现有 `RedissonLockTest` 的已知问题，避免静态 `@Container` 先于守卫启动）

### 3. `frame-me-starter-sensi-encrypt` 测试

- 现有测试不变（`DecryptedPropertySource` 改 public 不影响行为）
- 补一个测试验证 `DecryptedPropertySource` 可被外部包（cloud 模块）实例化

## 文档同步（实现完成后）

遵循项目约定（`project-memory` critical 指令：代码/配置改动后必须同步更新 CLAUDE.md 和 docs/）：

| 文档 | 更新内容 |
|---|---|
| `docs/modules.md` | 新增 `frame-me-starter-cloud` 与 `frame-me-starter-cloud-nacos` 模块章节；更新 `frame-me-starter-cloud` 从占位到云基础底座 |
| `docs/reference.md` | 更新第 383-385 行"BOM 已声明但未使用"为已使用；新增 cloud / cloud-nacos 关键类路径 |
| `docs/conventions.md` | 在"模块装配约定"补充 cloud 的刷新解密抽象作为模式 A 的示例；新增"Nacos 配置约定"小节（namespace=环境、敏感值分类） |
| `docs/architecture.md` | 更新模块分层图，加入 cloud / cloud-nacos |
| `CLAUDE.md` | 若命令速查或模块清单需更新，同步 |
| 模块依赖速查表（`docs/modules.md` 末尾） | 新增 cloud / cloud-nacos 行 |

## 不做的事（YAGNI）

- **不造 `me.cloud.nacos.*` 配置前缀**：SCA 原生 `spring.cloud.nacos.*` 足够，不重复造。
- **不引 OpenFeign**：现有 HTTP Interface + RestClient 够用。
- **不预埋 Gateway/Sentinel/Seata**：具体云组件按需引入，各自独立模块。
- **不做 namespace 映射表**：原生 `${spring.profiles.active}` 占位符覆盖 namespace=环境约定。
- **不做 ME 热更新生效**：敏感值变更走重启，不引入敏感值动态生效的复杂度。
- **不拆 config/discovery 两个模块**：先合后拆，当前一个模块够用。

## 风险与边界

1. **Java 25 运行时风险**：SCA 2025.1.0.0 基线 JDK 17，Java 25 向前兼容。引入后跑 `./mvnw -pl frame-me-starter-cloud-nacos dependency:tree` 检查传递依赖，重点关注是否有显式声明高于 25 的构件。集成测试验证连通性。
2. **短窗口期密文裸露**：`EnvironmentChangeEvent` 到监听器跑完之间，`@RefreshScope` bean 重建读密文会踩坑。约束：敏感配置 bean 不用 `@RefreshScope`。
3. **主密码永不进 Nacos**：`me.encrypt.password` 只走环境变量 / `-D` 启动参数，不写入 Nacos 远程配置。若主密码也放 Nacos 且写成 `ME(密文)`，会形成"解密自己"的循环依赖。文档写死此约定。
4. **SCA BOM 基准是 Spring Boot 4.0.0**：项目用 4.0.7，同一兼容线，SCA 通过 import BOM 不硬编码 Spring Boot，版本由项目 parent 接管，无冲突。

## 实现步骤（转入 writing-plans 时细化）

1. `frame-me-starter-sensi-encrypt`：`DecryptedPropertySource` 改 public（class + 构造器）
2. `frame-me-starter-cloud`：pom 加 `spring-cloud-commons` + optional sensi-encrypt；新增 `RefreshDecryptListener` + `CloudAutoConfiguration` + `AutoConfiguration.imports`
3. `frame-me-starter-cloud-nacos`：新建模块，pom 引两个 nacos starter + cloud；新增 `NacosCloudConstant`；根 pom `<modules>` 加该模块
4. 测试：cloud 切片测试 + AbsentClasspath 测试；cloud-nacos AbsentClasspath 测试
5. 文档同步：`docs/modules.md` / `reference.md` / `conventions.md` / `architecture.md` / 模块依赖速查表
6. 集成验证（可选）：tester-service 加 Nacos 集成测试

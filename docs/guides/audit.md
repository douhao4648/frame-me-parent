# 审计/行为日志（Audit Log）

`frame-me-starter-op-audit` 提供无侵入的业务行为日志记录能力：在方法上标注 `@AuditLog`，即可自动记录动作、分类、参数、返回值、异常、耗时，并输出到日志。

## 快速开始

业务 `xx-service` 已引入 `frame-me-boot` 时，能力自动生效，无需额外依赖。

```java
@Service
public class UserService {

    @AuditLog(action = "创建用户", category = "用户管理",
              description = "创建用户 #user.username，手机号 #user.phone")
    public User createUser(CreateUserRequest user) {
        return ...;
    }
}
```

日志输出示例：

```text
[AUDIT] {"action":"创建用户","category":"用户管理","description":"创建用户 alice，手机号 13800138000","operatorId":"anonymous","success":true,"durationMs":12,"params":"{\"user\":{\"username\":\"alice\",\"phone\":\"13800138000\"}}","result":"{\"id\":1,\"username\":\"alice\"}","timestamp":"2026-06-28T06:00:00Z"}
```

## 配置项

前缀 `me.audit`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用审计模块 |
| `log-enabled` | `true` | 是否在本地打印审计日志（仅打印本实例产生的事件，按 `sourceInstanceId` 与 `me.event-bridge.instance-id` 比对；其他实例广播来的事件由审计中心专用消费者处理，不在各实例重复打印） |
| `target-service` | `""` | 配置为审计服务名时经事件桥接定向发送；为空且 `broadcast=false` 时仅本地发布（不产生跨服务流量） |
| `broadcast` | `false` | 为 `true` 时即使 `target-service` 为空也经事件桥接广播（全员送达）；接收侧需 `@Import(AuditLogEventConfiguration.class)` 才会订阅通道 |
| `max-param-length` | `8192` | 参数与返回值 JSON 的最大字节长度，0 表示不限制；超限按 UTF-8 字节截断并追加 `...`，多字节字符不会被截半 |

> 与事件桥接的关系：桥接开启（`me.event-bridge.enabled=true`，默认）且配置了远程目标（`target-service` 或 `broadcast=true`）时审计事件经 `EventBridgePublisher` 本地发布 + 跨服务发送；未配置远程目标或桥接关闭时仅本地发布（`AuditLogLogger` 等本进程监听器照常消费），不产生跨服务流量。`EventBridgeProperties` 在桥接关闭时由 `AuditAutoConfiguration` 兜底注册，保证自身过滤语义一致。

```yaml
me:
  audit:
    target-service: audit-service
    max-param-length: 2000
```

## 占位符

`description` 支持 Spring SpEL 占位符：

- `#paramName` / `#paramName.xxx`：引用方法参数及其属性。
- `#result` / `#result.xxx`：引用返回值。
- `#error` / `#error.message`：引用异常对象。
- `#arg0`、`#arg1`：参数名不可用时按索引引用。

示例：

```java
@AuditLog(action = "更新订单", description = "更新订单 #orderId 状态为 #status，结果 #result.success")
public OrderUpdateResult updateOrderStatus(Long orderId, String status) { ... }
```

> ⚠️ **安全提示——占位符绕过 record 开关**：`description` 的 SpEL 占位符解析**不受** `recordParams`/`recordResult`/`recordError` 控制。即使设 `recordParams=false`（不记录入参），`description` 里写的 `#user.password` 仍会被解析成真实值写入 description 字段并随事件广播。**禁止在 description 中引用敏感参数字段**；脱敏应通过参数序列化层处理（见下）。

## 敏感参数脱敏

`@AuditLog` 的 `recordParams` 默认 `true`，会把方法入参整体 JSON 序列化进审计记录并跨服务广播。starter 不内置字段级脱敏（无法知晓哪些字段是密码/token），**脱敏由业务在序列化层负责**：

- 设 `recordParams=false` 完全不记入参（最保守）。
- 或在传入对象上用 fastjson2 的 `@JSONField(serialize = false)` 排除敏感字段。
- 或为 `AuditLogAspect` 提供自定义参数序列化扩展点（后续可加 `excludeParamNames` 配置）。

## starter 预置审计点

`frame-me-starter-auth-sa-token` 与 `frame-me-starter-auth-jwt` 的默认认证端点（登录/登出/续期或刷新/管理员强制登出）已预标 `@AuditLog`（op-audit 在两个认证 starter 中均为 optional 依赖，消费方未引入 op-audit 时注解被 JVM 静默忽略、无任何影响）。其中 login/refresh 刻意 `recordParams=false, recordResult=false`——明文密码与签发的 Token 不进审计，登录账号由 description 的 `#dto.account` 带出，登录失败经 `recordError` 留失败审计。业务自定义 Controller 参照此口径处理敏感字段。

## 跨服务持久化

配置 `me.audit.target-service` 后，`AuditLogEvent` 会携带 `targetService` 通过事件桥接定向发布；若要让所有订阅者都收到（而非定向中心），改配 `me.audit.broadcast: true`（两者都不配则仅本地发布，不产生跨服务流量）：

- 非审计服务收到消息后，因 `targetService` 不匹配而忽略，避免重复落库。
- **审计中心（接收侧）需显式启用订阅**：在启动类或任意配置类加 `@Import(AuditLogEventConfiguration.class)`（`com.frame.me.op.audit`），注册 `AuditLogEventType` 后 `EventBridgeListener` 才会订阅 `audit:op-log` 通道并还原消息。该配置刻意不随自动装配生效——发送侧经 `EventBridgePublisher` 直发不查注册表，自动注册只会让无关服务白订阅通道。
- 审计服务还原 `AuditLogEvent` 后，可自定义 `@EventListener` 或持久化监听器写入数据库/ES。

```java
@Component
public class AuditLogPersistenceHandler {

    @EventListener
    public void onAuditLog(AuditLogEvent event) {
        AuditLogRecord record = event.getRecord();
        // 写入审计表或 Elasticsearch
    }
}
```

> 注意：当前基于 Redis Pub/Sub 的传输是广播且非持久化的，审计服务离线会丢消息。若需要强一致审计，可后续实现 MQ transport，`IEventTransport` 接口已预留。

## 操作人上下文

默认操作人为 `anonymous`。接入认证模块后，提供 `IAuditLogOperatorSupplier` Bean 覆盖即可：

```java
@Bean
public IAuditLogOperatorSupplier auditLogOperatorSupplier() {
    return () -> SecurityContextHolder.getContext().getAuthentication().getName();
}
```

> SPI 异常容错：`IAuditLogOperatorSupplier.getOperatorId()` 抛异常时，切面降级为 `anonymous` 并打 warn，**不阻断被审计的业务方法**。审计是旁路，绝不能因审计自身故障影响主流程。

## 关闭日志输出

若仅希望把审计事件桥接到审计服务，而不在当前服务打印日志：

```yaml
me:
  audit:
    log-enabled: false
    target-service: audit-service
```

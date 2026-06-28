# 审计/行为日志（Audit Log）

`frame-me-starter-op-audit` 提供无侵入的业务行为日志记录能力：在方法上标注 `@AuditLog`，即可自动记录动作、分类、参数、返回值、异常、耗时，并输出到日志。

## 快速开始

业务 `xx-service` 已引入 `frame-me-booter` 时，能力自动生效，无需额外依赖。

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
| `log-enabled` | `true` | 是否在本地打印审计日志 |
| `target-service` | `""` | 为空时只走本地事件；配置为审计服务名时，通过事件桥接定向发送 |
| `max-param-length` | `0` | 参数 JSON 最大长度，0 表示不限制 |

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

## 跨服务持久化

配置 `me.audit.target-service` 后，`AuditLogEvent` 会携带 `targetService` 通过事件桥接发布：

- 非审计服务收到消息后，因 `targetService` 不匹配而忽略，避免重复落库。
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

> 注意：当前基于 Redis Pub/Sub 的传输是广播且非持久化的，审计服务离线会丢消息。若需要强一致审计，可后续实现 MQ transport，`EventTransport` 接口已预留。

## 操作人上下文

默认操作人为 `anonymous`。接入认证模块后，提供 `AuditLogOperatorSupplier` Bean 覆盖即可：

```java
@Bean
public AuditLogOperatorSupplier auditLogOperatorSupplier() {
    return () -> SecurityContextHolder.getContext().getAuthentication().getName();
}
```

## 关闭日志输出

若仅希望把审计事件桥接到审计服务，而不在当前服务打印日志：

```yaml
me:
  audit:
    log-enabled: false
    target-service: audit-service
```

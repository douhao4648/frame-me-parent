package com.frame.me.audit.infrastructure;

/**
 * 审计中心占位常量类.
 *
 * <p>本模块是 {@code frame-me-audit} 聚合工程的启动服务：订阅 {@code audit:op-log} 通道，
 * 接收 {@code frame-me-starter-op-audit} 桥接来的审计事件并持久化。当前为骨架，
 * 后续按需补充实现（接收侧需 {@code @Import(AuditLogEventConfiguration.class)} 启用订阅）.</p>
 *
 * @author frame-me
 */
public final class AuditConstant {

    private AuditConstant() {
    }
}

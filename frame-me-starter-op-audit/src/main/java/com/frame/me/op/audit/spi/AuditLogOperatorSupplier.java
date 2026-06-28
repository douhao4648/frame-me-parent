package com.frame.me.op.audit.spi;

/**
 * 审计操作人提供者.
 *
 * <p>默认返回 {@code "anonymous"}，后续认证模块可提供真实实现，例如从 SecurityContext
 * 获取当前用户 ID。</p>
 *
 * @author frame-me
 */
public interface AuditLogOperatorSupplier {

    /**
     * 获取当前操作人标识.
     *
     * @return 操作人 ID，无上下文时返回默认值
     */
    String getOperatorId();
}

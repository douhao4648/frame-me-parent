package com.frame.me.auth.audit;

import com.frame.me.auth.core.AuthContext;
import com.frame.me.op.audit.spi.IAuditLogOperatorSupplier;

/**
 * 基于认证上下文的审计操作人提供者.
 *
 * <p>从 {@link AuthContext} 获取当前登录用户 ID，供审计模块记录操作人。</p>
 *
 * @author frame-me
 */
public class AuditAuthOperatorSupplier implements IAuditLogOperatorSupplier {

    @Override
    public String getOperatorId() {
        Long userId = AuthContext.getUserId();
        return userId == null ? "anonymous" : String.valueOf(userId);
    }
}

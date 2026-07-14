package com.frame.me.auth.rbac.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL 表达式 root 对象，提供 {@code role} 和 {@code perm} 函数.
 *
 * @author frame-me
 */
@Slf4j
public class AuthExpressionRoot {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 执行 SpEL 表达式.
     */
    public static boolean evaluate(String expression) {
        StandardEvaluationContext ctx = new StandardEvaluationContext(new AuthExpressionRoot());
        try {
            Boolean result = PARSER.parseExpression(expression).getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("SpEL 表达式执行异常: {}", expression, e);
            return false;
        }
    }

    @SuppressWarnings("unused")
    public boolean role(String role) {
        if (role == null) {
            return false;
        }
        return AuthPermissionHolder.getRoles().contains(role);
    }

    @SuppressWarnings("unused")
    public boolean perm(String resource) {
        return perm(resource, "*");
    }

    @SuppressWarnings("unused")
    public boolean perm(String resource, String action) {
        if (resource == null || action == null) {
            return false;
        }
        return AuthPermissionHolder.getPermissions().stream()
                .anyMatch(p -> p.matches(resource, action));
    }
}

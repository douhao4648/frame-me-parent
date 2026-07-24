package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 路径规则简化表达式求值器.
 *
 * <p>支持的表达式语法（非 SpEL）：
 * <ul>
 *   <li>{@code login} —— 校验已登录（{@link StpUtil#checkLogin()}）</li>
 *   <li>{@code role:xxx} —— 校验拥有角色 xxx（{@link StpUtil#checkRole(String)}）</li>
 *   <li>{@code perm:resource} —— 校验权限码 resource（{@link StpUtil#checkPermission(String)}）</li>
 *   <li>{@code perm:resource:action} —— 校验权限码 resource:action</li>
 * </ul>
 *
 * @author frame-me
 */
public final class SaTokenRuleEvaluator {

    private static final String PREFIX_ROLE = "role:";
    private static final String PREFIX_PERM = "perm:";

    private SaTokenRuleEvaluator() {
    }

    /**
     * 规则类型.
     */
    public enum Kind {
        /** 登录校验. */
        LOGIN,
        /** 角色校验. */
        ROLE,
        /** 权限校验（权限码原样透传，可为 resource 或 resource:action）. */
        PERMISSION
    }

    /**
     * 解析后的规则.
     *
     * @param kind  规则类型
     * @param value 角色标识或权限码（LOGIN 类型时为 {@code null}）
     */
    public record Rule(Kind kind, String value) {
    }

    /**
     * 解析简化表达式为 {@link Rule}，非法表达式抛 {@link IllegalArgumentException}.
     *
     * @param expression 表达式字符串
     * @return 解析结果
     */
    public static Rule parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        String expr = expression.trim();
        if ("login".equals(expr)) {
            return new Rule(Kind.LOGIN, null);
        }
        if (expr.startsWith(PREFIX_ROLE)) {
            String role = expr.substring(PREFIX_ROLE.length()).trim();
            if (role.isEmpty() || role.contains(":")) {
                throw new IllegalArgumentException("非法角色表达式: " + expression);
            }
            return new Rule(Kind.ROLE, role);
        }
        if (expr.startsWith(PREFIX_PERM)) {
            String perm = expr.substring(PREFIX_PERM.length()).trim();
            if (perm.isEmpty()) {
                throw new IllegalArgumentException("非法权限表达式: " + expression);
            }
            return new Rule(Kind.PERMISSION, perm);
        }
        throw new IllegalArgumentException("无法识别的表达式: " + expression
                + "，支持 login / role:xxx / perm:resource / perm:resource:action");
    }

    /**
     * 对当前请求执行规则校验.
     *
     * @param rule 已解析的规则
     */
    public static void check(Rule rule) {
        switch (rule.kind()) {
            case LOGIN -> StpUtil.checkLogin();
            case ROLE -> StpUtil.checkRole(rule.value());
            case PERMISSION -> StpUtil.checkPermission(rule.value());
            default -> throw new IllegalStateException("未知规则类型: " + rule.kind());
        }
    }
}

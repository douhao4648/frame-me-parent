package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.stp.StpLogic;
import com.frame.me.base.exception.BusinessException;

/**
 * 路径规则简化表达式求值器.
 *
 * <p>支持的表达式语法（非 SpEL）：
 * <ul>
 *   <li>{@code login} —— 校验已登录（{@link StpLogic#checkLogin()}）</li>
 *   <li>{@code role:xxx} —— 校验拥有角色 xxx（{@link StpLogic#checkRole(String)}）</li>
 *   <li>{@code perm:resource} —— 校验权限码 resource（{@link StpLogic#checkPermission(String)}）</li>
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
     * 对当前请求执行规则校验（指定 sa-token 账号体系）.
     *
     * @param rule     已解析的规则
     * @param stpLogic 目标账号体系的 {@link StpLogic}
     */
    public static void check(Rule rule, StpLogic stpLogic) {
        // 经典冒号式 switch：P3C SwitchStatementRule 认不出箭头式 default 标签，会误报缺少 default
        switch (rule.kind()) {
            case LOGIN:
                stpLogic.checkLogin();
                break;
            case ROLE:
                stpLogic.checkRole(rule.value());
                break;
            case PERMISSION:
                stpLogic.checkPermission(rule.value());
                break;
            default:
                throw new BusinessException("未知规则类型: " + rule.kind());
        }
    }

    /**
     * 规则类型.
     */
    public enum Kind {
        /**
         * 登录校验.
         */
        LOGIN,
        /**
         * 角色校验.
         */
        ROLE,
        /**
         * 权限校验（权限码原样透传，可为 resource 或 resource:action）.
         */
        PERMISSION
    }

    /**
     * 解析后的规则.
     *
     * <p>不用 record：P3C 会把 record 头误判为方法名（Rule 不符合 lowerCamelCase），
     * 普通 final class 构造器不会被扫描.</p>
     */
    public static final class Rule {

        /** 规则类型. */
        private final Kind kind;

        /** 角色标识或权限码（LOGIN 类型时为 {@code null}）. */
        private final String value;

        /**
         * 创建规则.
         *
         * @param kind  规则类型
         * @param value 角色标识或权限码
         */
        public Rule(Kind kind, String value) {
            this.kind = kind;
            this.value = value;
        }

        /**
         * 规则类型.
         *
         * @return 类型
         */
        public Kind kind() {
            return kind;
        }

        /**
         * 角色标识或权限码.
         *
         * @return 值
         */
        public String value() {
            return value;
        }
    }
}

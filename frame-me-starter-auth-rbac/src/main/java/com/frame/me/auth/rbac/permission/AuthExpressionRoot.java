package com.frame.me.auth.rbac.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 表达式 root 对象，提供功能权限函数 {@code role}/{@code perm}
 * 与数据权限函数 {@code dataIsAll}/{@code dataCheck}（{@code data} 前缀标识数据权限域，
 * 语义与 {@link AuthDataPermissions} 的 {@code isAll}/{@code check} 一致，实现委托之）.
 *
 * <p>表达式经 {@link SpelExpressionParser} 解析后按字符串缓存，避免每次请求重复构建 AST；
 * root 对象无状态，全局复用单例。</p>
 *
 * <p><b>安全约束：</b>求值使用 {@link SimpleEvaluationContext#forReadOnlyDataBinding()}
 * 并仅开启 root 实例方法调用，禁止 {@code T(...)} 类型引用、构造器、bean 解析等危险能力，
 * 因此表达式来源即使接入配置中心也不会导致远程代码执行。</p>
 *
 * @author frame-me
 */
@Slf4j
public class AuthExpressionRoot {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 表达式缓存上限，防止来源异常时缓存无界增长.
     */
    private static final int MAX_CACHE_SIZE = 1024;

    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    /**
     * 无状态 root，全局复用.
     */
    private static final AuthExpressionRoot ROOT = new AuthExpressionRoot();

    /**
     * 执行 SpEL 表达式.
     *
     * @param expression 表达式，求值结果为 {@code true} 时放行
     * @return 是否通过；表达式非法或求值异常时返回 {@code false}
     */
    public static boolean evaluate(String expression) {
        return evaluate(expression, null);
    }

    /**
     * 执行 SpEL 表达式，并把 {@code variables} 作为 SpEL 变量（{@code #名}）注入求值上下文.
     *
     * <p>典型用途：拦截器把 URI 路径变量注入表达式，如 {@code dataCheck('order', #id)}。</p>
     *
     * @param expression 表达式，求值结果为 {@code true} 时放行
     * @param variables  SpEL 变量（可为 {@code null}）
     * @return 是否通过；表达式非法或求值异常时返回 {@code false}
     */
    public static boolean evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            Expression exp = cachedExpression(expression);
            SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .withRootObject(ROOT)
                    .build();
            if (variables != null && !variables.isEmpty()) {
                variables.forEach(ctx::setVariable);
            }
            return Boolean.TRUE.equals(exp.getValue(ctx, Boolean.class));
        } catch (Exception e) {
            log.error("SpEL 表达式执行异常: {}", expression, e);
            return false;
        }
    }

    /**
     * 获取缓存的表达式，未命中时解析并按上限缓存.
     */
    private static Expression cachedExpression(String expression) {
        Expression exp = EXPRESSION_CACHE.get(expression);
        if (exp != null) {
            return exp;
        }
        exp = PARSER.parseExpression(expression);
        if (EXPRESSION_CACHE.size() < MAX_CACHE_SIZE) {
            EXPRESSION_CACHE.put(expression, exp);
        }
        return exp;
    }

    @SuppressWarnings("unused")
    public boolean role(String role) {
        if (role == null) {
            return false;
        }
        return AuthPermissionHolder.getRoles().contains(role);
    }

    /**
     * 判断是否拥有某资源的任意操作权限.
     *
     * @param resource 资源标识
     * @return 是否拥有
     */
    @SuppressWarnings("unused")
    public boolean perm(String resource) {
        if (resource == null) {
            return false;
        }
        return AuthPermissionHolder.getPermissions().stream()
                .anyMatch(p -> p.matchesResource(resource));
    }

    @SuppressWarnings("unused")
    public boolean perm(String resource, String action) {
        if (resource == null || action == null) {
            return false;
        }
        return AuthPermissionHolder.getPermissions().stream()
                .anyMatch(p -> p.matches(resource, action));
    }

    /**
     * 判断是否拥有某资源的 {@code ALL} 数据范围（同 {@link AuthDataPermissions#isAll}).
     *
     * @param resource 资源标识
     * @return 是否拥有
     */
    @SuppressWarnings("unused")
    public boolean dataIsAll(String resource) {
        return AuthDataPermissions.isAll(resource);
    }

    /**
     * 判断是否拥有某资源与操作的 {@code ALL} 数据范围（同 {@link AuthDataPermissions#isAll}).
     *
     * @param resource 资源标识
     * @param action   操作标识
     * @return 是否拥有
     */
    @SuppressWarnings("unused")
    public boolean dataIsAll(String resource, String action) {
        return AuthDataPermissions.isAll(resource, action);
    }

    /**
     * 判断是否可访问某资源的指定数据行（命中 {@code ALL} 范围或数据 ID 并集，
     * 同 {@link AuthDataPermissions#check}).
     *
     * @param resource 资源标识
     * @param dataId   数据行主键，支持 {@link Number} 或数字字符串
     * @return 是否可访问
     */
    @SuppressWarnings("unused")
    public boolean dataCheck(String resource, Object dataId) {
        return AuthDataPermissions.check(resource, dataId);
    }

    /**
     * 判断是否可访问某资源与操作的指定数据行（命中 {@code ALL} 范围或数据 ID 并集，
     * 同 {@link AuthDataPermissions#check}).
     *
     * @param resource 资源标识
     * @param action   操作标识
     * @param dataId   数据行主键，支持 {@link Number} 或数字字符串
     * @return 是否可访问
     */
    @SuppressWarnings("unused")
    public boolean dataCheck(String resource, String action, Object dataId) {
        return AuthDataPermissions.check(resource, action, dataId);
    }
}

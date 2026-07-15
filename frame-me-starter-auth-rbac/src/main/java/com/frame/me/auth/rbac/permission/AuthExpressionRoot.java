package com.frame.me.auth.rbac.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 表达式 root 对象，提供 {@code role} 和 {@code perm} 函数.
 *
 * <p>表达式经 {@link SpelExpressionParser} 解析后按字符串缓存，避免每次请求重复构建 AST；
 * root 对象无状态，全局复用单例。</p>
 *
 * <p><b>安全约束：</b>表达式被视为可信输入（来自开发者注解或受控配置）。
 * {@link StandardEvaluationContext} 允许 {@code T(...)} 类型引用与反射调用，
 * 因此切勿把表达式来源接入低权限可写的配置中心，否则存在远程代码执行风险。</p>
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
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            Expression exp = cachedExpression(expression);
            StandardEvaluationContext ctx = new StandardEvaluationContext(ROOT);
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
}

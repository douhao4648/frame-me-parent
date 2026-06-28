package com.frame.me.op.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录审计日志的方法.
 *
 * <p>切面会在方法执行前后收集动作、参数、返回值、耗时与异常信息，并发布
 * {@link com.frame.me.op.audit.core.AuditLogEvent}。</p>
 *
 * <p>{@code description} 支持 {@code #paramName.xxx} 形式的占位符，例如
 * {@code 创建用户 #user.username}；方法执行后还可使用 {@code #result} 引用返回值，
 * 异常场景可使用 {@code #error} 引用异常对象。</p>
 *
 * @author frame-me
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 操作动作，为空时默认取 {@code ClassName#methodName}.
     *
     * @return 操作动作
     */
    String action() default "";

    /**
     * 操作分类，用于后续聚合检索.
     *
     * @return 分类
     */
    String category() default "";

    /**
     * 操作描述，支持 SpEL 占位符.
     *
     * @return 描述
     */
    String description() default "";

    /**
     * 是否记录方法入参.
     *
     * @return 是否记录入参
     */
    boolean recordParams() default true;

    /**
     * 是否记录返回值.
     *
     * @return 是否记录返回值
     */
    boolean recordResult() default true;

    /**
     * 是否记录异常信息.
     *
     * @return 是否记录异常
     */
    boolean recordError() default true;
}

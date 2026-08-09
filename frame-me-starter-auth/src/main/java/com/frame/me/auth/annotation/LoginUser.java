package com.frame.me.auth.annotation;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户注解.
 *
 * <p>标注在 Controller 方法参数上，自动从 {@link com.frame.me.auth.core.AuthContext} 注入当前用户。</p>
 *
 * <p>{@code @Parameter(hidden = true)} 元注解：该参数由服务端从认证上下文解析、非客户端传参，
 * springdoc 经 {@code findMergedAnnotation} 识别后在 OpenAPI 文档中整体忽略，
 * 业务侧无需逐个使用点标注。swagger-annotations 经 frame-me-api 传递引入（全项目非 optional），
 * 下游未启用 springdoc 时注解被 JVM 忽略，不影响运行时。</p>
 *
 * @author frame-me
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface LoginUser {
}

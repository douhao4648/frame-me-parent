package com.frame.me.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户注解.
 *
 * <p>标注在 Controller 方法参数上，自动从 {@link com.frame.me.auth.core.AuthContext} 注入当前用户。</p>
 *
 * @author frame-me
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}

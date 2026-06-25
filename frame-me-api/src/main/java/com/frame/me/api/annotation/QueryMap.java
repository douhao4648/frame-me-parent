package com.frame.me.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记声明式 HTTP 接口客户端方法参数：将该查询对象（POJO）按属性展开为 URL query 参数.
 *
 * <p>声明式 HTTP 接口客户端（{@code @HttpExchange} / {@code @ImportHttpServices}）的调用端代理不支持
 * {@code @ModelAttribute}，也没有"POJO → query 参数"的内置解析能力。在查询对象参数上标注本注解，即可由
 * {@code QueryObjectArgumentResolver} 将其字段展开为 query 参数。</p>
 *
 * <p>服务端 Spring MVC 会忽略本注解，仍按 {@code @ModelAttribute}（query → bean）默认绑定，故同一契约
 * 接口可被客户端代理与服务端 Controller 共用。</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryMap {
}

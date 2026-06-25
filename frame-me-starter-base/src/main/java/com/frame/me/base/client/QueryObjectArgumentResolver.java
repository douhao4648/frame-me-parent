package com.frame.me.base.client;

import com.frame.me.api.annotation.QueryMap;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

import java.beans.PropertyDescriptor;
import java.util.Collection;

/**
 * 将标注 {@link QueryMap} 的查询对象（POJO）按 JavaBean 属性展开为 URL query 参数.
 *
 * <p>声明式 HTTP 接口客户端（{@code @HttpExchange} / {@code @ImportHttpServices}）的调用端代理不支持
 * {@code @ModelAttribute}，也没有内置的"POJO → query 参数"解析能力。本解析器补齐该能力，等价于服务端
 * {@code @ModelAttribute} 绑定（query → bean）的调用端逆操作。</p>
 */
public class QueryObjectArgumentResolver implements HttpServiceArgumentResolver {

    @Override
    public boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
        if (!parameter.hasParameterAnnotation(QueryMap.class)) {
            return false;
        }
        if (argument != null) {
            addBeanProperties(argument, requestValues);
        }
        return true;
    }

    private void addBeanProperties(Object bean, HttpRequestValues.Builder requestValues) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean);
        for (PropertyDescriptor descriptor : wrapper.getPropertyDescriptors()) {
            String name = descriptor.getName();
            if ("class".equals(name) || descriptor.getReadMethod() == null) {
                continue;
            }
            Object value = wrapper.getPropertyValue(name);
            if (value == null) {
                continue;
            }
            // ponytail: 仅平铺一层属性（嵌套对象走 String.valueOf），对扁平查询对象足够；如需嵌套展开再扩展.
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null) {
                        requestValues.addRequestParameter(name, String.valueOf(item));
                    }
                }
            } else {
                requestValues.addRequestParameter(name, String.valueOf(value));
            }
        }
    }
}

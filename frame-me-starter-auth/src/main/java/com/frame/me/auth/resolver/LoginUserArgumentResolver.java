package com.frame.me.auth.resolver;

import com.frame.me.auth.annotation.LoginUser;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUser} 参数解析器.
 *
 * <p>将标注了 {@link LoginUser} 的方法参数解析为 {@link AuthContext} 中的当前用户。</p>
 *
 * @author frame-me
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return AuthContext.getUser();
    }
}

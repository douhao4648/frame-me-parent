package com.frame.me.adapter.advice;

import com.frame.me.adapter.result.Response;
import com.frame.me.api.result.IResult;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 将 {@link IResult} 统一转换为 {@link Response} 后返回给前端.
 */
@ControllerAdvice
public class Result2ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        return IResult.class.isAssignableFrom(parameterType)
                && !Response.class.isAssignableFrom(parameterType);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Response) {
            return body;
        }
        if (!(body instanceof IResult<?> result)) {
            return body;
        }
        Response<Object> resp = new Response<>();
        resp.setCode(result.getCode());
        resp.setMessage(result.getMsg());
        resp.setResult(result.getData());
        return resp;
    }
}

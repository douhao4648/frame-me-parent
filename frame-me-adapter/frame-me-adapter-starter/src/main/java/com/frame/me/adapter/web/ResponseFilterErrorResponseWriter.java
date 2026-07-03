package com.frame.me.adapter.web;

import tools.jackson.databind.ObjectMapper;
import com.frame.me.adapter.result.Response;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 老接口规范下的 Filter 层错误响应写入器：输出 {@link Response} 格式 JSON.
 *
 * <p>当业务工程引入 {@code frame-me-adapter-starter} 时，该实现会覆盖
 * {@code frame-me-starter-base} 的默认 {@code Result} 格式实现，使 Filter 层错误响应
 * 与 Controller 层的外部响应格式保持一致。</p>
 *
 * @author frame-me
 */
public class ResponseFilterErrorResponseWriter implements IFilterErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ResponseFilterErrorResponseWriter(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(HttpServletResponse response, ResultCode resultCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String msg = message == null ? resultCode.getMsg() : message;
        Response<?> result = new Response<>(resultCode.getCode(), msg, null, null);
        objectMapper.writeValue(response.getWriter(), result);
        response.getWriter().flush();
    }
}

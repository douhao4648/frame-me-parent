package com.frame.me.base.web;

import tools.jackson.databind.ObjectMapper;
import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 默认的 Filter 层错误响应写入器：输出 {@link Result} 格式 JSON.
 *
 * @author frame-me
 */
public class ResultFilterErrorResponseWriter implements IFilterErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ResultFilterErrorResponseWriter(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(HttpServletResponse response, ResultCode resultCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        IResult<?> result = message == null ? Result.error(resultCode) : Result.error(resultCode, message);
        objectMapper.writeValue(response.getWriter(), result);
        response.getWriter().flush();
    }
}

package com.frame.me.base.web;

import com.frame.me.base.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Filter 层错误响应写入器 SPI.
 *
 * <p>Servlet Filter 运行在 {@code DispatcherServlet} 之前，其抛出的异常无法被
 * {@link org.springframework.web.bind.annotation.RestControllerAdvice} 捕获，
 * 因此需要在 Filter 内部直接输出错误响应。本接口允许各模块/项目自定义 Filter 层错误响应格式。</p>
 *
 * @author frame-me
 */
public interface IFilterErrorResponseWriter {

    /**
     * 将错误响应写入 {@link HttpServletResponse}.
     *
     * @param response   响应对象
     * @param resultCode 状态码枚举
     * @param message    错误消息；为 {@code null} 时使用 {@code resultCode} 默认消息
     * @throws IOException 写入失败时抛出
     */
    void write(HttpServletResponse response, ResultCode resultCode, String message) throws IOException;
}

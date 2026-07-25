package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局异常处理配置属性.
 *
 * <p>绑定前缀 {@code me.exception}，控制异常信息是否包含完整堆栈等对外暴露行为。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.exception")
public class ExceptionProperties {

    /**
     * 全局异常响应是否包含完整堆栈，默认 true.
     *
     * <p>开启时，{@link com.frame.me.base.advice.GlobalExceptionHandler} 会在 {@code Result.err}
     * 中写入异常完整堆栈；关闭时仅保留 {@code Result.msg} 中的通用/异常消息，避免堆栈中的类路径、
     * 参数等敏感信息随响应体泄漏。</p>
     */
    private boolean includeStacktrace = true;
}

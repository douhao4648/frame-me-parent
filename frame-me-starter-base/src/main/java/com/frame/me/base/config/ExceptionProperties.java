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
     * 全局异常响应是否包含完整堆栈，默认 false.
     *
     * <p>开启时，{@link com.frame.me.base.advice.GlobalExceptionHandler} 会在 {@code Result.err}
     * 中写入异常完整堆栈；关闭时仅保留 {@code Result.msg} 中的通用/异常消息，避免堆栈中的类路径、
     * 参数等敏感信息随响应体泄漏。仅建议在排查问题时显式开启，生产环境保持关闭。</p>
     */
    private boolean includeStacktrace = false;

    /**
     * 兜底未知异常对外是否屏蔽真实 message，默认 false（返回异常自身 message）.
     *
     * <p>开启时，兜底 {@code Exception} 处理器对外固定返回通用文案（"系统错误"），真实 message
     * 只进服务端日志，避免 SQL、类路径、内网地址等内部细节随响应体泄漏；对外服务建议开启。</p>
     */
    private boolean maskUnknownMessage = true;
}

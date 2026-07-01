package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 异步任务线程池配置属性.
 *
 * <p>绑定前缀 {@code me.async}，用于配置默认 {@code @Async} 线程池参数。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.async")
public class AsyncProperties {

    /**
     * 是否启用默认异步配置，默认 true.
     */
    private boolean enabled = true;

    /**
     * 核心线程数，默认 4.
     */
    private int corePoolSize = 4;

    /**
     * 最大线程数，默认 16.
     */
    private int maxPoolSize = 16;

    /**
     * 任务队列容量，默认 256.
     */
    private int queueCapacity = 256;

    /**
     * 非核心线程空闲存活时间（秒），默认 60.
     */
    private int keepAliveSeconds = 60;

    /**
     * 线程名前缀，默认 me-async-.
     */
    private String threadNamePrefix = "me-async-";

    /**
     * 是否允许核心线程超时回收，默认 false.
     */
    private boolean allowCoreThreadTimeOut = false;

    /**
     * 应用关闭时等待任务完成的最大秒数，0 表示不等待，默认 0.
     */
    private int awaitTerminationSeconds = 0;

    /**
     * 拒绝策略，默认 CALLER_RUNS.
     *
     * <p>可选值：ABORT、CALLER_RUNS、DISCARD、DISCARD_OLDEST。</p>
     */
    private String rejectionPolicy = "CALLER_RUNS";

    /**
     * 是否注册默认异步异常处理器，默认 true.
     */
    private boolean exceptionHandlerEnabled = true;

    /**
     * 异步异常发生时是否尝试通过 {@link com.frame.me.base.notify.INotifySender} 发送通知，默认 true.
     */
    private boolean exceptionNotifyEnabled = true;

    /**
     * 异步异常通知的接收者列表，默认空列表。
     *
     * <p>若为空，则由具体的 {@link com.frame.me.base.notify.INotifySender} 实现决定是否使用全局默认接收者。
     * 例如 frame-me-starter-msg-notify 会回退到 {@code me.notify.global-receivers}。</p>
     */
    private List<String> exceptionNotifyReceivers = new ArrayList<>();
}

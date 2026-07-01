package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度任务线程池配置属性.
 *
 * <p>绑定前缀 {@code me.scheduling}，用于配置默认 {@code @Scheduled} 调度线程池参数。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.scheduling")
public class SchedulingProperties {

    /**
     * 是否启用默认调度配置，默认 true.
     */
    private boolean enabled = true;

    /**
     * 线程池大小，默认 4.
     */
    private int poolSize = 4;

    /**
     * 线程名前缀，默认 me-scheduling-.
     */
    private String threadNamePrefix = "me-scheduling-";

    /**
     * 取消任务后是否立即从线程池中移除，默认 false.
     */
    private boolean removeOnCancelPolicy = false;

    /**
     * 应用关闭时等待任务完成的最大秒数，0 表示不等待，默认 0.
     */
    private int awaitTerminationSeconds = 0;

    /**
     * 是否注册默认调度异常处理器，默认 true.
     */
    private boolean exceptionHandlerEnabled = true;

    /**
     * 调度任务异常时是否尝试通过 {@link com.frame.me.base.notify.INotifySender} 发送通知，默认 true.
     */
    private boolean exceptionNotifyEnabled = true;

    /**
     * 调度异常通知的接收者列表，默认空列表。
     *
     * <p>若为空，则由具体的 {@link com.frame.me.base.notify.INotifySender} 实现决定是否使用全局默认接收者。
     * 例如 frame-me-starter-msg-notify 会回退到 {@code me.notify.global-receivers}。</p>
     */
    private List<String> exceptionNotifyReceivers = new ArrayList<>();
}

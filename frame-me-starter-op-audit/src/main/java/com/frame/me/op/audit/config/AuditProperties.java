package com.frame.me.op.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计日志配置项.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.audit")
public class AuditProperties {

    /**
     * 是否启用审计模块，默认 true.
     */
    private boolean enabled = true;

    /**
     * 是否在本地打印审计日志，默认 true.
     */
    private boolean logEnabled = true;

    /**
     * 审计服务名. 配置为具体服务名时经事件桥接定向发送；为空且 {@link #broadcast} 为
     * false 时仅本地发布（不产生跨服务流量）.
     */
    private String targetService = "";

    /**
     * 是否广播到所有订阅者，默认 false.
     *
     * <p>为 true 时即使 {@link #targetService} 为空也经事件桥接广播（全员送达语义）；
     * 与 target-service 互斥不冲突——target-service 非空时优先生效定向发送。
     * 接收侧需 {@code @Import(AuditLogEventConfiguration.class)} 才会订阅通道。</p>
     */
    private boolean broadcast = false;

    /**
     * 参数 JSON 最大长度，默认 8192（8KB），0 表示不限制.
     */
    private int maxParamLength = 8192;

    /**
     * 异步发布配置：审计事件的 publish 提交到专用线程池，避免阻塞业务线程.
     *
     * <p>同步模式下，{@code publish} 内的 {@code bridge.publish} 若走 HTTP transport 会拖长业务 RT。
     * 异步模式把 publish 提交到有界队列 + 专用线程池，队列满时丢弃审计（不阻塞业务）.</p>
     */
    private Async async = new Async();

    @Data
    public static class Async {

        /**
         * 是否启用异步发布，默认 true.
         *
         * <p>关闭后退回同步 publish（审计不丢、但阻塞业务线程）.</p>
         */
        private boolean enabled = true;

        /**
         * 核心线程数，默认 1.
         */
        private int corePoolSize = 1;

        /**
         * 最大线程数，默认 4.
         */
        private int maxPoolSize = 4;

        /**
         * 队列容量，默认 1024. 队列满时丢弃审计（不阻塞业务）.
         */
        private int queueCapacity = 1024;
    }
}

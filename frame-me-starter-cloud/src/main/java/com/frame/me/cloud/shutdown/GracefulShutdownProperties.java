package com.frame.me.cloud.shutdown;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 优雅下线编排配置.
 *
 * <p>云平台滚动发布时，容器收到 SIGTERM 到真正被 kill 之间有窗口（K8s 默认
 * {@code terminationGracePeriodSeconds=30s}）。直接关闭 Tomcat 会导致：
 * LB 健康检查未失败新请求继续打进来、Nacos 注册表实例还在消费者拉到调用失败、
 * 在途请求未处理完被 kill。本模块编排多步骤下线：标记 health DOWN → 反注册 →
 * 等待消费者刷新缓存 → Spring 原生 graceful shutdown 处理在途请求.</p>
 *
 * <p>两条触发路径，幂等：
 * <ul>
 *   <li>preStop 调 {@code POST /actuator/offline}（主路径，SIGTERM 前完成编排）</li>
 *   <li>{@link GracefulShutdownListener} 监听 {@code ContextClosedEvent}（兜底，SIGTERM 后触发）</li>
 * </ul>
 * preStap 调过端点后 flag 已 false、已反注册，SIGTERM 来时 listener 再跑一遍无副作用.</p>
 *
 * @author frame-me
 */
@ConfigurationProperties(prefix = "me.cloud.shutdown")
public class GracefulShutdownProperties {

    /**
     * 是否启用优雅下线编排.
     */
    private boolean enabled = true;

    /**
     * 反注册后等待消费者刷新本地缓存的时长.
     *
     * <p>Nacos 客户端有缓存刷新窗口，反注册后消费者可能仍持有旧实例；
     * 等待一段时间让缓存更新，避免滚动发布期间请求打到正在关闭的实例.</p>
     */
    private Duration deregisterWait = Duration.ofSeconds(15);

    /**
     * 是否注册 actuator health indicator（联动 health 端点）.
     */
    private boolean healthIndicatorEnabled = true;

    /**
     * 是否暴露 {@code POST /actuator/offline} 主动下线端点.
     */
    private boolean endpointEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDeregisterWait() {
        return deregisterWait;
    }

    public void setDeregisterWait(Duration deregisterWait) {
        this.deregisterWait = deregisterWait;
    }

    public boolean isHealthIndicatorEnabled() {
        return healthIndicatorEnabled;
    }

    public void setHealthIndicatorEnabled(boolean healthIndicatorEnabled) {
        this.healthIndicatorEnabled = healthIndicatorEnabled;
    }

    public boolean isEndpointEnabled() {
        return endpointEnabled;
    }

    public void setEndpointEnabled(boolean endpointEnabled) {
        this.endpointEnabled = endpointEnabled;
    }
}

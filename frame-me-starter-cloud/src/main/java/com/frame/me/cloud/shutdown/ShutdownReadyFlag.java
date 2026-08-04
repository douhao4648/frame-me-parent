package com.frame.me.cloud.shutdown;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 下线就绪标志.
 *
 * <p>默认 {@code true}（服务就绪，health UP）。下线编排开始时置 {@code false}，
 * actuator health indicator 与业务 HealthController 都读它联动返回 DOWN，
 * 使 LB 健康检查立即失败、停止发新流量.</p>
 *
 * <p>两条触发路径（preStap 端点 / ContextClosedEvent listener）都置同一个实例，
 * 幂等：已 {@code false} 再 set 无副作用.</p>
 *
 * @author frame-me
 */
public class ShutdownReadyFlag {

    private final AtomicBoolean ready = new AtomicBoolean(true);

    /**
     * 服务是否就绪（health UP）.
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * 标记服务进入下线状态（health DOWN）.
     * 幂等：多次调用无副作用.
     */
    public void markShuttingDown() {
        ready.set(false);
    }
}

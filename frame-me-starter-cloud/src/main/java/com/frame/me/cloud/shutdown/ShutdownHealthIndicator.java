package com.frame.me.cloud.shutdown;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 下线就绪 health indicator：读 {@link ShutdownReadyFlag} 联动 actuator health 端点.
 *
 * <p>服务就绪（flag=true）返回 UP；进入下线（flag=false）返回 OUT_OF_SERVICE，
 * 使打 management 端口 {@code /actuator/health} 的 LB 探针立即失败、停止发新流量.</p>
 *
 * @author frame-me
 */
public class ShutdownHealthIndicator implements HealthIndicator {

    private final ShutdownReadyFlag flag;

    public ShutdownHealthIndicator(ShutdownReadyFlag flag) {
        this.flag = flag;
    }

    @Override
    public Health health() {
        if (flag.isReady()) {
            return Health.up().build();
        }
        return Health.outOfService().withDetail("reason", "graceful shutdown in progress").build();
    }
}

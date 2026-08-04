package com.frame.me.cloud.shutdown;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShutdownHealthIndicator} 测试.
 *
 * @author frame-me
 */
class ShutdownHealthIndicatorTest {

    @Test
    void readyFlag_returnsUp() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        ShutdownHealthIndicator indicator = new ShutdownHealthIndicator(flag);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shuttingDownFlag_returnsOutOfService() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        flag.markShuttingDown();
        ShutdownHealthIndicator indicator = new ShutdownHealthIndicator(flag);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("reason", "graceful shutdown in progress");
    }
}

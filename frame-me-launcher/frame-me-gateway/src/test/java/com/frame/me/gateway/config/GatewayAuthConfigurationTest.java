package com.frame.me.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayAuthConfiguration} 装配边界测试.
 *
 * @author frame-me
 */
class GatewayAuthConfigurationTest {

    /**
     * user-validator=sa-token 但无 Redis 配置：启动 fail-fast（防静默裸奔）.
     */
    @Test
    void saTokenValidatorWithoutRedis_failsFast() {
        GatewayAuthConfiguration config = new GatewayAuthConfiguration();
        ObjectProvider<ReactiveStringRedisTemplate> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> config.saTokenUserValidator(new GatewayAuthProperties(), emptyProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis");
    }
}

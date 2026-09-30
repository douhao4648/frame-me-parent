package com.frame.me.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;
import java.util.Map;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayAuthConfiguration} 装配边界测试.
 *
 * @author frame-me
 */
class GatewayAuthConfigurationTest {

    @Test
    void bindsDigestMemoryThresholdWithSizeUnits() {
        var source = new MapConfigurationPropertySource(Map.of("me.gateway.auth.digest-memory-threshold", "1MB"));
        GatewayAuthProperties properties = new Binder(source).bind("me.gateway.auth", GatewayAuthProperties.class).get();
        assertThat(properties.getDigestMemoryThreshold().toBytes()).isEqualTo(1024 * 1024);
        assertThat(new GatewayAuthProperties().getDigestMemoryThreshold().toBytes()).isEqualTo(256 * 1024);
    }

    @Test
    void bindsDigestCacheDirectoryAndKeepsCurrentDefault() {
        var source = new MapConfigurationPropertySource(Map.of("me.gateway.auth.digest-cache-directory", "/tmp/custom-digest-cache"));
        GatewayAuthProperties properties = new Binder(source).bind("me.gateway.auth", GatewayAuthProperties.class).get();
        assertThat(properties.getDigestCacheDirectory()).isEqualTo(Path.of("/tmp/custom-digest-cache"));
        assertThat(new GatewayAuthProperties().getDigestCacheDirectory())
                .isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
    }

    @Test
    void bindsDigestResourceLimits() {
        var source = new MapConfigurationPropertySource(Map.of(
                "me.gateway.auth.digest-max-size", "10MB",
                "me.gateway.auth.digest-read-timeout", "30s",
                "me.gateway.auth.digest-cache-max-age", "12h"));
        GatewayAuthProperties properties = new Binder(source).bind("me.gateway.auth", GatewayAuthProperties.class).get();
        assertThat(properties.getDigestMaxSize().toBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(properties.getDigestReadTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(properties.getDigestCacheMaxAge()).isEqualTo(java.time.Duration.ofHours(12));
    }

    @Test
    void invalidDigestThresholdFailsAuthenticatorCreation() {
        GatewayAuthProperties properties = new GatewayAuthProperties();
        properties.setDigestMemoryThreshold(DataSize.ofBytes(-1));
        ObjectProvider<ReactiveStringRedisTemplate> emptyProvider = mock(ObjectProvider.class);
        assertThatThrownBy(() -> new GatewayAuthConfiguration().configAppAuthenticator(properties, emptyProvider))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("digest-memory-threshold");
    }

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

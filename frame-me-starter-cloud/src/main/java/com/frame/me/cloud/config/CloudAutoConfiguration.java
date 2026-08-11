package com.frame.me.cloud.config;

import com.frame.me.cloud.shutdown.*;
import com.frame.me.encrypt.env.DecryptedPropertySource;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * 云基础底座自动装配入口.
 *
 * <p>本配置类装配三类能力：
 * <ul>
 *   <li>优雅下线编排（{@code ShutdownReadyFlag} + health indicator + endpoint + listener）</li>
 *   <li>刷新解密（{@link RefreshDecryptAutoConfiguration}，依赖 sensi-encrypt，optional）</li>
 * </ul>
 *
 * <p>反注册抽象（{@link ServiceRegistry} / {@link Registration}）在 spring-cloud-commons，
 * 由 {@link GracefulShutdownExecutor} 用 {@link ObjectProvider} 守卫注入——无注册中心时
 * 跳过反注册，只做 health 联动 + 等待（遵循 {@code docs/conventions.md} 模式 C）.</p>
 *
 * @author frame-me
 */
@AutoConfiguration
@EnableConfigurationProperties(GracefulShutdownProperties.class)
public class CloudAutoConfiguration {

    /**
     * 下线就绪标志：无条件装配，actuator health indicator 与业务 HealthController 都可注入联动.
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.cloud.shutdown", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ShutdownReadyFlag shutdownReadyFlag() {
        return new ShutdownReadyFlag();
    }

    /**
     * 下线编排核心：注册中心无关（用 ObjectProvider 守卫 ServiceRegistry/Registration）.
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.cloud.shutdown", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GracefulShutdownExecutor gracefulShutdownExecutor(ShutdownReadyFlag flag,
                                                             GracefulShutdownProperties properties,
                                                             ObjectProvider<ServiceRegistry<Registration>> serviceRegistryProvider,
                                                             ObjectProvider<Registration> registrationProvider) {
        return new GracefulShutdownExecutor(flag, properties, serviceRegistryProvider, registrationProvider);
    }

    /**
     * actuator health indicator 联动：flag false 时返回 OUT_OF_SERVICE.
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnBean(ShutdownReadyFlag.class)
    @ConditionalOnProperty(prefix = "me.cloud.shutdown", name = {"enabled", "health-indicator-enabled"},
            havingValue = "true", matchIfMissing = true)
    public HealthIndicator gracefulShutdownHealthIndicator(ShutdownReadyFlag flag) {
        return new ShutdownHealthIndicator(flag);
    }

    /**
     * 主动下线端点 POST /actuator/offline：preStop 主路径.
     */
    @Bean
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnBean(GracefulShutdownExecutor.class)
    @ConditionalOnProperty(prefix = "me.cloud.shutdown", name = {"enabled", "endpoint-enabled"},
            havingValue = "true", matchIfMissing = true)
    public GracefulShutdownEndpoint gracefulShutdownEndpoint(GracefulShutdownExecutor executor,
                                                             GracefulShutdownProperties properties) {
        return new GracefulShutdownEndpoint(executor, properties);
    }

    /**
     * ContextClosedEvent 兜底监听器：preStop 未配/失败时，SIGTERM 来了兜住.
     */
    @Bean
    @ConditionalOnBean(GracefulShutdownExecutor.class)
    @ConditionalOnProperty(prefix = "me.cloud.shutdown", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationListener<?> gracefulShutdownListener(GracefulShutdownExecutor executor) {
        return new GracefulShutdownListener(executor);
    }

    /**
     * 刷新解密能力装配：仅在 sensi-encrypt 在 classpath 且暴露 {@link StringEncryptor} Bean
     * （即配置了 {@code me.encrypt.password}）时装配.
     *
     * <p>{@link StringEncryptor} Bean 由 sensi-encrypt 的 {@code EncryptAutoConfiguration}
     * 在自动装配阶段注册，早于运行时 refresh，监听器能正常装配.</p>
     *
     * <p>独立 static 内部类隔离 sensi-encrypt 引用，缺席时整体退避不触发 NCDFE（模式 A）.</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({EnumerablePropertySource.class, DecryptedPropertySource.class, StringEncryptor.class})
    @ConditionalOnBean(StringEncryptor.class)
    static class RefreshDecryptAutoConfiguration {

        @Bean
        public ApplicationListener<?> refreshDecryptListener(ConfigurableEnvironment environment,
                                                             ObjectProvider<StringEncryptor> encryptorProvider) {
            // @ConditionalOnBean(StringEncryptor.class) 已保证 Bean 存在时才装配，故 getIfAvailable 非空
            return new RefreshDecryptListener(environment, encryptorProvider.getIfAvailable());
        }
    }
}

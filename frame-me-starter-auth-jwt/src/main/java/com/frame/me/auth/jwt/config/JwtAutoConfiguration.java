package com.frame.me.auth.jwt.config;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.jwt.core.*;
import com.frame.me.auth.jwt.web.JwtAuthController;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.limit.LoginRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 认证自动配置.
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.auth.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtAuthProperties.class)
@AutoConfigureBefore(name = "com.frame.me.auth.config.AuthAutoConfiguration")
public class JwtAutoConfiguration {

    /**
     * 内存兜底存储：未引入 multi-redis 且业务未自定义 {@link IRefreshTokenStore} 时装配.
     *
     * <p>单实例可用；多实例部署 Refresh Token 不跨实例共享，应引入
     * {@code frame-me-starter-multi-redis} 切换为 Redis 存储。</p>
     */
    @Bean
    @ConditionalOnMissingBean(IRefreshTokenStore.class)
    public IRefreshTokenStore inMemoryRefreshTokenStore() {
        log.warn("未检测到 frame-me-starter-multi-redis，JWT Refresh Token 将使用内存存储（单实例可用）。"
                + "多实例部署必须引入 frame-me-starter-multi-redis 以启用 Redis 存储后端。");
        return new InMemoryRefreshTokenStore();
    }

    /**
     * JWT 认证服务，接管 {@link IAuthService}.
     *
     * <p>SuppressWarnings：{@code IAuthUserDetailsService} 由业务工程实现，本模块内
     * 无 Bean，IDEA 的自动注入检查属误报（运行时由业务模块提供实现）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(IAuthService.class)
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public IAuthService jwtAuthService(JwtAuthProperties properties,
                                       IAuthUserDetailsService userDetailsService,
                                       IRefreshTokenStore refreshTokenStore) {
        log.info("JwtTokenService initialized");
        return new JwtTokenService(properties, userDetailsService, refreshTokenStore);
    }

    @Bean
    @ConditionalOnMissingBean(IAuthUserResolver.class)
    public IAuthUserResolver jwtAuthUserResolver(JwtAuthProperties properties, IAuthService authService) {
        log.info("JwtAuthUserResolver initialized");
        return new JwtAuthUserResolver(properties, authService);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthController jwtAuthController(IAuthService authService, JwtAuthProperties properties,
                                               AuthProperties authProperties,
                                               ObjectProvider<LoginRateLimiter> loginRateLimiter) {
        return new JwtAuthController(authService, properties, authProperties, loginRateLimiter);
    }

    /**
     * Redis 存储装配：classpath 存在 multi-redis 时激活，业务自定义
     * {@link IRefreshTokenStore} 优先.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.frame.me.redis.util.RedisUtils")
    static class RedisRefreshTokenStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(IRefreshTokenStore.class)
        public IRefreshTokenStore redisRefreshTokenStore(JwtAuthProperties properties) {
            return new RedisRefreshTokenStore(properties);
        }
    }
}

package com.frame.me.auth.jwt.config;

import com.frame.me.auth.jwt.core.IAuthUserDetailsService;
import com.frame.me.auth.jwt.core.JwtAuthUserResolver;
import com.frame.me.auth.jwt.core.JwtTokenService;
import com.frame.me.auth.jwt.core.RedisRefreshTokenStore;
import com.frame.me.auth.jwt.core.RefreshTokenStore;
import com.frame.me.auth.jwt.web.JwtAuthController;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserResolver;
import lombok.extern.slf4j.Slf4j;
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

    @Bean
    @ConditionalOnMissingBean(RefreshTokenStore.class)
    public RefreshTokenStore refreshTokenStore(JwtAuthProperties properties) {
        return new RedisRefreshTokenStore(properties);
    }

    @Bean
    @ConditionalOnMissingBean(IAuthService.class)
    public IAuthService jwtAuthService(JwtAuthProperties properties,
                                       IAuthUserDetailsService userDetailsService,
                                       RefreshTokenStore refreshTokenStore) {
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
    public JwtAuthController jwtAuthController(IAuthService authService, JwtAuthProperties properties) {
        return new JwtAuthController(authService, properties);
    }
}

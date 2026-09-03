package com.frame.me.gateway.config;

import com.frame.me.gateway.auth.*;
import com.frame.me.gateway.filter.GatewayAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关鉴权装配.
 *
 * <p>用户 token 验证器按 {@code me.gateway.auth.user-validator} 二选一（体系级开关，
 * 与下游统一认证底座对应）：jwt 装配 {@link JwtUserValidator}（无状态）；
 * sa-token 装配 {@link SaTokenRedisUserValidator}（需共享 Redis，缺失 fail-fast）。
 * 实例级开关 {@code user-auth-enabled}/{@code app-auth-enabled} 关闭后对应认证器
 * 不装配，过滤器对该类凭证直接 401（用于纯配置拆出 app 专属等实例）.</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayAuthConfiguration {

    /**
     * JWT 用户 token 验证器（默认）.
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.gateway.auth", name = "user-validator",
            havingValue = "jwt", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "me.gateway.auth", name = "user-auth-enabled",
            havingValue = "true", matchIfMissing = true)
    public IUserValidator jwtUserValidator(GatewayAuthProperties properties) {
        log.info("网关用户 token 验证器：jwt（本地验签，无状态）");
        return new JwtUserValidator(properties.getJwt());
    }

    /**
     * sa-token 用户 token 验证器：直查共享 Redis，无 Redis 配置启动 fail-fast.
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.gateway.auth", name = "user-validator", havingValue = "sa-token")
    @ConditionalOnProperty(prefix = "me.gateway.auth", name = "user-auth-enabled",
            havingValue = "true", matchIfMissing = true)
    public IUserValidator saTokenUserValidator(GatewayAuthProperties properties,
                                               ObjectProvider<ReactiveStringRedisTemplate> redisProvider) {
        ReactiveStringRedisTemplate redisTemplate = redisProvider.getIfAvailable();
        if (redisTemplate == null) {
            throw new IllegalStateException(
                    "me.gateway.auth.user-validator=sa-token 需要配置 spring.data.redis（与下游共用的 Redis 实例），"
                            + "当前无 ReactiveStringRedisTemplate bean，启动失败");
        }
        log.info("网关用户 token 验证器：sa-token（直查共享 Redis，踢人即时生效）");
        return new SaTokenRedisUserValidator(redisTemplate, properties.getSaToken());
    }

    /**
     * 配置版应用认证器（app 签名凭证）.
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.gateway.auth", name = "app-auth-enabled",
            havingValue = "true", matchIfMissing = true)
    public IAppAuthenticator configAppAuthenticator(GatewayAuthProperties properties) {
        return new ConfigAppAuthenticator(properties.getApps());
    }

    /**
     * 匿名访问守卫：allow-anonymous=true 必须显式激活 internal profile，否则启动失败（fail-closed）.
     */
    @Bean
    public AnonymousAccessGuard anonymousAccessGuard(Environment environment, GatewayAuthProperties properties) {
        return new AnonymousAccessGuard(environment, properties);
    }

    /**
     * 网关鉴权全局过滤器（认证器按实例级开关可缺省，缺失即该类凭证 401）.
     */
    @Bean
    public GatewayAuthFilter gatewayAuthFilter(GatewayAuthProperties properties,
                                               ObjectProvider<IUserValidator> userValidatorProvider,
                                               ObjectProvider<IAppAuthenticator> appAuthenticatorProvider,
                                               ObjectMapper objectMapper) {
        return new GatewayAuthFilter(properties, userValidatorProvider, appAuthenticatorProvider, objectMapper);
    }
}

package com.frame.me.auth.jwt.config;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.jwt.core.IRefreshTokenStore;
import com.frame.me.auth.jwt.core.InMemoryRefreshTokenStore;
import com.frame.me.auth.jwt.core.RedisRefreshTokenStore;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtAutoConfiguration} 的 Refresh Token 存储条件装配测试.
 *
 * <p>multi-redis 是 auth-jwt 的 optional 依赖（本模块 test classpath 恒有，
 * optional 只阻断传递），故用 {@link FilteredClassLoader} 屏蔽
 * {@code com.frame.me.redis} 包模拟缺席场景。</p>
 *
 * @author frame-me
 */
class JwtRefreshTokenStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtAutoConfiguration.class))
            // jwtAuthService 启动期校验 secret（fail-fast），装配测试需显式提供
            .withPropertyValues("me.auth.jwt.secret=frame-me-jwt-secret-key-at-least-32-characters-long")
            .withBean(AuthProperties.class)
            .withBean(IAuthUserDetailsService.class, () -> new IAuthUserDetailsService() {
                @Override
                public User loadUserByAccount(String account) {
                    return null;
                }

                @Override
                public User loadUserById(Long id) {
                    return null;
                }

                @Override
                public boolean matches(String rawPassword, String encodedPassword) {
                    return false;
                }
            });

    /**
     * multi-redis 在场：装配 Redis 存储，不加载内存兜底.
     */
    @Test
    void redisPresent_redisStoreActive() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IRefreshTokenStore.class);
            assertThat(context.getBean(IRefreshTokenStore.class))
                    .isInstanceOf(RedisRefreshTokenStore.class);
        });
    }

    /**
     * multi-redis 缺席：Redis 存储配置在条件评估阶段退避（不抛 NCDFE），
     * 回退为内存兜底存储（单实例可用，装配时打 WARN）.
     */
    @Test
    void redisAbsent_inMemoryStoreFallback() {
        runner.withClassLoader(new FilteredClassLoader("com.frame.me.redis"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IRefreshTokenStore.class);
                    assertThat(context.getBean(IRefreshTokenStore.class))
                            .isInstanceOf(InMemoryRefreshTokenStore.class);
                });
    }

    /**
     * 业务自定义 IRefreshTokenStore 时，两种默认实现都让位.
     */
    @Test
    void customStoreTakesPrecedence() {
        IRefreshTokenStore custom = new IRefreshTokenStore() {
            @Override
            public void save(Long userId, String refreshToken, java.time.Duration expires) {
            }

            @Override
            public String get(Long userId) {
                return null;
            }

            @Override
            public void delete(Long userId) {
            }
        };
        runner.withBean(IRefreshTokenStore.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IRefreshTokenStore.class);
                    assertThat(context.getBean(IRefreshTokenStore.class)).isSameAs(custom);
                });
    }
}

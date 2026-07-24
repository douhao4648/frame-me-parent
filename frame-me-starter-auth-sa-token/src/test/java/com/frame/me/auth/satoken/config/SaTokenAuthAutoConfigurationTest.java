package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpInterface;
import com.frame.me.auth.satoken.advice.SaTokenExceptionAdvice;
import com.frame.me.auth.satoken.core.RedisSaTokenDao;
import com.frame.me.auth.satoken.core.SaTokenAuthService;
import com.frame.me.auth.satoken.core.SaTokenAuthUserResolver;
import com.frame.me.auth.satoken.permission.ConfigStpInterface;
import com.frame.me.auth.satoken.web.SaTokenAuthController;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sa-Token 认证装配测试：验证默认 bean 装配、开关退避与业务接管.
 *
 * <p>本模块 test classpath 恒有 multi-redis（optional 只阻断传递），
 * 故 {@link SaTokenRedisDaoAutoConfiguration} 在 runner 中默认激活，注册
 * {@link RedisSaTokenDao}（构造期不连接 Redis，可纯内存验证装配关系）。</p>
 *
 * @author frame-me
 */
class SaTokenAuthAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SaTokenAuthAutoConfiguration.class, SaTokenRedisDaoAutoConfiguration.class))
            .withUserConfiguration(StubUserDetailsConfig.class);

    /**
     * 默认装配：接管 IAuthService / IAuthUserResolver，注册 Controller / Advice /
     * StpInterface / Redis 会话 DAO；本模块<b>不</b>声明 SaTokenConfig——
     * 原生配置由官方 starter 的 SaBeanRegister 绑定 {@code sa-token.*} 提供.
     */
    @Test
    void defaultWiring() {
        runner.run(context -> {
            assertThat(context.getBean(IAuthService.class)).isInstanceOf(SaTokenAuthService.class);
            assertThat(context.getBean(IAuthUserResolver.class)).isInstanceOf(SaTokenAuthUserResolver.class);
            assertThat(context).hasSingleBean(SaTokenAuthController.class);
            assertThat(context).hasSingleBean(SaTokenExceptionAdvice.class);
            assertThat(context.getBean(StpInterface.class)).isInstanceOf(ConfigStpInterface.class);
            // 护栏：本模块不得声明任何形式的自有 SaTokenConfig Bean（原生配置走官方 sa-token.*）
            assertThat(context).doesNotHaveBean(SaTokenConfig.class);
            assertThat(context.getBean(SaTokenDao.class)).isInstanceOf(RedisSaTokenDao.class);
            assertThat(context).hasSingleBean(SaTokenAuthProperties.class);
        });
    }

    /**
     * 总开关关闭：me.auth.sa-token.enabled=false 时两个配置类一并退避.
     */
    @Test
    void disabled_backsOffEntirely() {
        runner.withPropertyValues("me.auth.sa-token.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IAuthService.class);
                    assertThat(context).doesNotHaveBean(IAuthUserResolver.class);
                    assertThat(context).doesNotHaveBean(SaTokenAuthController.class);
                    assertThat(context).doesNotHaveBean(SaTokenExceptionAdvice.class);
                    assertThat(context).doesNotHaveBean(StpInterface.class);
                    assertThat(context).doesNotHaveBean(SaTokenConfig.class);
                    assertThat(context).doesNotHaveBean(SaTokenDao.class);
                });
    }

    /**
     * redis 开关单独关闭：Redis DAO 退避（sa-token 运行时回退内存 DAO），其余 bean 不受影响.
     */
    @Test
    void redisDisabled_redisDaoBacksOff() {
        runner.withPropertyValues("me.auth.sa-token.redis.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SaTokenDao.class);
                    assertThat(context).hasSingleBean(IAuthService.class);
                    assertThat(context).hasSingleBean(StpInterface.class);
                });
    }

    /**
     * 业务声明 IAuthService / IAuthUserResolver / StpInterface 时，默认实现退避.
     */
    @Test
    void businessBeans_overrideDefaults() {
        runner.withUserConfiguration(BusinessOverrideConfig.class)
                .run(context -> {
                    assertThat(context.getBean(IAuthService.class)).isInstanceOf(BusinessAuthService.class);
                    assertThat(context.getBean(IAuthUserResolver.class)).isInstanceOf(BusinessUserResolver.class);
                    assertThat(context.getBean(StpInterface.class)).isInstanceOf(BusinessStpInterface.class);
                });
    }

    /**
     * 用户查询 SPI 桩：默认实现均为返回 null / false 的空实现，仅满足注入.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubUserDetailsConfig {
        @Bean
        IAuthUserDetailsService authUserDetailsService() {
            return new IAuthUserDetailsService() {
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
            };
        }
    }

    /**
     * 业务接管 Bean 声明.
     */
    @Configuration(proxyBeanMethods = false)
    static class BusinessOverrideConfig {
        @Bean
        IAuthService businessAuthService() {
            return new BusinessAuthService();
        }

        @Bean
        IAuthUserResolver businessUserResolver() {
            return new BusinessUserResolver();
        }

        @Bean
        StpInterface businessStpInterface() {
            return new BusinessStpInterface();
        }
    }

    /**
     * 业务认证服务空实现.
     */
    static class BusinessAuthService implements IAuthService {
        @Override
        public String login(String account, String password) {
            return null;
        }

        @Override
        public void logout(String credential) {
        }

        @Override
        public String refresh(String credential) {
            return null;
        }

        @Override
        public boolean validate(String credential) {
            return false;
        }

        @Override
        public User getUser(String credential) {
            return null;
        }
    }

    /**
     * 业务用户解析器空实现.
     */
    static class BusinessUserResolver implements IAuthUserResolver {
        @Override
        public User resolve(jakarta.servlet.http.HttpServletRequest request) {
            return null;
        }
    }

    /**
     * 业务权限数据源空实现（StpInterface 方法无默认实现，需覆盖）.
     */
    static class BusinessStpInterface implements StpInterface {
        @Override
        public java.util.List<String> getPermissionList(Object loginId, String loginType) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<String> getRoleList(Object loginId, String loginType) {
            return java.util.List.of();
        }
    }
}

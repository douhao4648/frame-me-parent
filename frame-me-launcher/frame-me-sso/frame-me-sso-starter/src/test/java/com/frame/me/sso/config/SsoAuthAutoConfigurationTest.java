package com.frame.me.sso.config;

import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.user.User;
import com.frame.me.sso.api.IAuthApi;
import com.frame.me.sso.api.IUserApi;
import com.frame.me.sso.auth.SsoAuthUserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link SsoAuthAutoConfiguration} 装配测试：{@link SsoAuthUserDetailsServiceImpl} 的
 * 兜底/退让语义（{@code @ConditionalOnMissingBean}）.
 *
 * @author frame-me
 */
class SsoAuthAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SsoAuthAutoConfiguration.class))
            .withBean(IAuthApi.class, () -> mock(IAuthApi.class))
            .withBean(IUserApi.class, () -> mock(IUserApi.class))
            .withBean(IAuthService.class, () -> mock(IAuthService.class))
            .withBean(SsoClientProperties.class, SsoClientProperties::new);

    /**
     * 下游未自定义 IAuthUserDetailsService（RP 无本地用户表）→ 兜底装配.
     */
    @Test
    void providesFallbackUserDetailsServiceWhenAbsent() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SsoAuthUserDetailsServiceImpl.class);
            assertThat(context.getBean(IAuthUserDetailsService.class))
                    .isInstanceOf(SsoAuthUserDetailsServiceImpl.class);
        });
    }

    /**
     * 下游自定义 IAuthUserDetailsService（有本地用户表）→ 自动退让，不装配兜底实现.
     */
    @Test
    void backsOffWhenUserDefinesOwn() {
        runner.withBean(IAuthUserDetailsService.class, () -> new IAuthUserDetailsService() {
            @Override
            public User loadUserByAccount(String account) {
                return null;
            }

            @Override
            public User loadUserById(Long id) {
                return null;
            }
        }).run(context -> {
            assertThat(context).doesNotHaveBean(SsoAuthUserDetailsServiceImpl.class);
            assertThat(context).hasSingleBean(IAuthUserDetailsService.class);
        });
    }
}

package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AnonymousAccessGuard} 测试.
 *
 * @author frame-me
 */
class AnonymousAccessGuardTest {

    @Test
    void internalProfile_withAllowAnonymous_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("internal");
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setAllowAnonymous(true);

        assertThatCode(() -> new AnonymousAccessGuard(env, props)).doesNotThrowAnyException();
    }

    @Test
    void publicProfile_withAllowAnonymous_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("public");
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setAllowAnonymous(true);

        assertThatThrownBy(() -> new AnonymousAccessGuard(env, props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal");
    }

    @Test
    void noTopologyProfile_withAllowAnonymous_fails() {
        // 忘记配置拓扑 profile：fail-closed，匿名配置拒绝启动
        MockEnvironment env = new MockEnvironment();
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setAllowAnonymous(true);

        assertThatThrownBy(() -> new AnonymousAccessGuard(env, props))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noProfile_withoutAllowAnonymous_passes() {
        MockEnvironment env = new MockEnvironment();
        GatewayAuthProperties props = new GatewayAuthProperties();

        assertThatCode(() -> new AnonymousAccessGuard(env, props)).doesNotThrowAnyException();
    }

    @Test
    void guardDisabled_skipsTopologyCheck() {
        // 显式关闭守卫：allow-anonymous=true 无 internal 也放行（用户明确放弃防呆保护）
        MockEnvironment env = new MockEnvironment();
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setAllowAnonymous(true);
        props.setAnonymousGuardEnabled(false);

        assertThatCode(() -> new AnonymousAccessGuard(env, props)).doesNotThrowAnyException();
    }
}

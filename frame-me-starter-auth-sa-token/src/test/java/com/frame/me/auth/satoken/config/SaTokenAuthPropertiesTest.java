package com.frame.me.auth.satoken.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SaTokenAuthProperties} 配置绑定测试.
 *
 * <p>sa-token 原生参数（token-name / timeout / active-timeout / is-concurrent /
 * is-share / cookie.*）已移交官方 {@code sa-token.*} 配置路径，本类仅承载框架自有配置。</p>
 *
 * @author frame-me
 */
class SaTokenAuthPropertiesTest {

    /**
     * 默认值：enabled=true、path=/api/auth、rules/roles/users 为空、redis 两件套默认.
     */
    @Test
    void defaults() {
        SaTokenAuthProperties properties = new SaTokenAuthProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPath()).isEqualTo("/api/auth");
        assertThat(properties.getRules()).isEmpty();
        assertThat(properties.getRoles()).isEmpty();
        assertThat(properties.getUsers()).isEmpty();
        assertThat(properties.getRedis().isEnabled()).isTrue();
        assertThat(properties.getRedis().getClientName()).isEqualTo("default");
    }

    /**
     * 全量绑定：标量、嵌套 redis、Map（含方括号 key）.
     */
    @Test
    void bind_allFields() {
        Map<String, Object> source = new HashMap<>();
        source.put("me.auth.sa-token.enabled", "false");
        source.put("me.auth.sa-token.path", "/auth");
        source.put("me.auth.sa-token.rules[/api/admin/**]", "role:admin");
        source.put("me.auth.sa-token.rules[/api/order/**]", "perm:order:read");
        source.put("me.auth.sa-token.roles.admin", "user:add,order:read");
        source.put("me.auth.sa-token.users.1", "admin,operator");
        source.put("me.auth.sa-token.redis.enabled", "false");
        source.put("me.auth.sa-token.redis.client-name", "auth");

        SaTokenAuthProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("me.auth.sa-token", Bindable.of(SaTokenAuthProperties.class))
                .get();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getPath()).isEqualTo("/auth");
        assertThat(properties.getRules())
                .containsEntry("/api/admin/**", "role:admin")
                .containsEntry("/api/order/**", "perm:order:read");
        assertThat(properties.getRoles()).containsEntry("admin", "user:add,order:read");
        assertThat(properties.getUsers()).containsEntry("1", "admin,operator");
        assertThat(properties.getRedis().isEnabled()).isFalse();
        assertThat(properties.getRedis().getClientName()).isEqualTo("auth");
    }

    /**
     * 反例：未用方括号记法的规则 key 会被 relaxed binding 剥离 {@code /} 与 {@code *}，
     * 启动时由 {@link SaTokenAuthProperties#validateRuleKeys()} 抛异常 fail-fast.
     */
    @Test
    void bind_ruleKeyWithoutBrackets_getsStripped_andFailsFast() {
        Map<String, Object> source = new HashMap<>();
        source.put("me.auth.sa-token.rules./api/admin/**", "role:admin");

        SaTokenAuthProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("me.auth.sa-token", Bindable.of(SaTokenAuthProperties.class))
                .get();

        assertThat(properties.getRules()).doesNotContainKey("/api/admin/**");
        assertThat(properties.getRules().keySet()).allSatisfy(key -> assertThat(key).doesNotStartWith("/"));
        assertThatThrownBy(properties::validateRuleKeys)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是以 '/' 开头的有效路径");
    }
}

package com.frame.me.auth.satoken.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SaTokenRuleEvaluator} 路径规则表达式解析测试.
 *
 * @author frame-me
 */
class SaTokenRuleEvaluatorTest {

    /**
     * {@code login} → 登录校验规则.
     */
    @Test
    void parse_login() {
        SaTokenRuleEvaluator.Rule rule = SaTokenRuleEvaluator.parse("login");
        assertThat(rule.kind()).isEqualTo(SaTokenRuleEvaluator.Kind.LOGIN);
        assertThat(rule.value()).isNull();

        // 容忍首尾空白
        assertThat(SaTokenRuleEvaluator.parse("  login  ").kind())
                .isEqualTo(SaTokenRuleEvaluator.Kind.LOGIN);
    }

    /**
     * {@code role:xxx} → 角色校验规则.
     */
    @Test
    void parse_role() {
        SaTokenRuleEvaluator.Rule rule = SaTokenRuleEvaluator.parse("role:admin");
        assertThat(rule.kind()).isEqualTo(SaTokenRuleEvaluator.Kind.ROLE);
        assertThat(rule.value()).isEqualTo("admin");
    }

    /**
     * {@code perm:resource} 与 {@code perm:resource:action} → 权限校验规则，权限码原样透传.
     */
    @Test
    void parse_perm() {
        SaTokenRuleEvaluator.Rule resourceOnly = SaTokenRuleEvaluator.parse("perm:order");
        assertThat(resourceOnly.kind()).isEqualTo(SaTokenRuleEvaluator.Kind.PERMISSION);
        assertThat(resourceOnly.value()).isEqualTo("order");

        SaTokenRuleEvaluator.Rule withAction = SaTokenRuleEvaluator.parse("perm:order:read");
        assertThat(withAction.kind()).isEqualTo(SaTokenRuleEvaluator.Kind.PERMISSION);
        assertThat(withAction.value()).isEqualTo("order:read");
    }

    /**
     * 非法表达式：空、无法识别、缺角色 / 权限码、角色多冒号.
     */
    @Test
    void parse_invalidExpressions() {
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse("spel:role('admin')"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse("role:"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse("role:a:b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaTokenRuleEvaluator.parse("perm:"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

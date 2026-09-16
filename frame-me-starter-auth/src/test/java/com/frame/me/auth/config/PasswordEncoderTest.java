package com.frame.me.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    @Test
    void configuredEncoderHashesAndMatchesPasswords() {
        AuthProperties properties = new AuthProperties();
        properties.setBcryptStrength(4);
        PasswordEncoder encoder = new AuthAutoConfiguration(properties).passwordEncoder();

        String encoded = encoder.encode("123456");

        assertThat(encoded).isNotEqualTo("123456");
        assertThat(encoder.matches("123456", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }
}

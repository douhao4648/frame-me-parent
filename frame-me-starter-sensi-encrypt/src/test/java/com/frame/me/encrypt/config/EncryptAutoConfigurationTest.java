package com.frame.me.encrypt.config;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EncryptAutoConfiguration} 条件装配测试.
 *
 * <p>用 {@link ApplicationContextRunner} 验证：配置主密码时暴露可往返加解密的
 * {@link StringEncryptor} Bean；未配置时不暴露。无需启动完整应用。</p>
 */
class EncryptAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EncryptAutoConfiguration.class));

    @Test
    void shouldExposeEncryptorWhenPasswordSet() {
        runner.withPropertyValues("frame.me.encrypt.password=master-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(StringEncryptor.class);
                    StringEncryptor encryptor = context.getBean(StringEncryptor.class);
                    assertThat(encryptor.decrypt(encryptor.encrypt("hello"))).isEqualTo("hello");
                });
    }

    @Test
    void shouldNotExposeEncryptorWhenPasswordMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(StringEncryptor.class));
    }
}

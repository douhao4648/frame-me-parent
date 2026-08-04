package com.frame.me.cloud.config;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.env.DecryptedPropertySource;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RefreshDecryptListener} 刷新解密测试.
 *
 * <p>验证配置中心运行时刷新后，新拉取的含 {@code ME(密文)} 属性源被重新包装成
 * {@link DecryptedPropertySource}，且幂等（已包装的源不重复包装）.</p>
 *
 * @author frame-me
 */
class RefreshDecryptListenerTest {

    private static final String PASSWORD = "frame-me-test-master-password";
    private static final String PLAINTEXT = "my-db-password-123";

    private final StringEncryptor encryptor = JasyptEncryptor.create(
            PASSWORD, EncryptConstant.DEFAULT_ALGORITHM, EncryptConstant.DEFAULT_ITERATIONS);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudAutoConfiguration.class))
            .withBean(StringEncryptor.class, () -> encryptor)
            .withPropertyValues("me.encrypt.password=" + PASSWORD);

    /**
     * 刷新前注入一个含 ME(密文) 的原始属性源，模拟配置中心新拉取的源.
     * 刷新事件后该源应被替换为 DecryptedPropertySource，getProperty 拿到明文.
     */
    @Test
    void afterRefresh_encryptedSourceRewrappedToDecrypted() {
        runner.run(context -> {
            ConfigurableEnvironment env = context.getEnvironment();
            String cipher = "ME(" + encryptor.encrypt(PLAINTEXT) + ")";

            // 模拟刷新后注入的原始含密文源（未包装）
            Map<String, Object> map = new HashMap<>();
            map.put("test.db.password", cipher);
            env.getPropertySources().addFirst(new MapPropertySource("nacos:test.yml", map));

            // 触发刷新事件
            context.publishEvent(new EnvironmentChangeEvent(Set.of("test.db.password")));

            // 验证源被包装成 DecryptedPropertySource
            PropertySource<?> source = env.getPropertySources().get("nacos:test.yml");
            assertThat(source).isInstanceOf(DecryptedPropertySource.class);
            // 验证解密拿到明文
            assertThat(env.getProperty("test.db.password")).isEqualTo(PLAINTEXT);
        });
    }

    /**
     * 幂等：已是 DecryptedPropertySource 的源不再重复包装.
     * 连续两次刷新事件后，源仍是同一个 DecryptedPropertySource 实例（不会被包成嵌套包装器）.
     */
    @Test
    void afterRefresh_idempotent_alreadyWrappedNotReWrapped() {
        runner.run(context -> {
            ConfigurableEnvironment env = context.getEnvironment();
            String cipher = "ME(" + encryptor.encrypt(PLAINTEXT) + ")";

            Map<String, Object> map = new HashMap<>();
            map.put("test.db.password", cipher);
            env.getPropertySources().addFirst(new MapPropertySource("nacos:test.yml", map));

            // 第一次刷新：包装
            context.publishEvent(new EnvironmentChangeEvent(Set.of("test.db.password")));
            PropertySource<?> afterFirst = env.getPropertySources().get("nacos:test.yml");
            assertThat(afterFirst).isInstanceOf(DecryptedPropertySource.class);

            // 第二次刷新：不重复包装，仍是同一实例
            context.publishEvent(new EnvironmentChangeEvent(Set.of("test.db.password")));
            PropertySource<?> afterSecond = env.getPropertySources().get("nacos:test.yml");
            assertThat(afterSecond).isSameAs(afterFirst);
            assertThat(env.getProperty("test.db.password")).isEqualTo(PLAINTEXT);
        });
    }

    /**
     * 无密文的源刷新后不包装（hasEncryptedProperties 返回 false）.
     */
    @Test
    void afterRefresh_plaintextSourceNotWrapped() {
        runner.run(context -> {
            ConfigurableEnvironment env = context.getEnvironment();
            Map<String, Object> map = new HashMap<>();
            map.put("test.plain.key", "plain-value");
            env.getPropertySources().addFirst(new MapPropertySource("nacos:plain.yml", map));

            context.publishEvent(new EnvironmentChangeEvent(Set.of("test.plain.key")));

            // 无密文，源保持原类型未被包装
            PropertySource<?> source = env.getPropertySources().get("nacos:plain.yml");
            assertThat(source).isInstanceOf(MapPropertySource.class);
            assertThat(source).isNotInstanceOf(DecryptedPropertySource.class);
        });
    }

    /**
     * 未配主密码（无 StringEncryptor Bean）时，CloudAutoConfiguration.RefreshDecryptAutoConfiguration
     * 不装配，刷新后含密文源不会被解密.
     */
    @Test
    void noMasterPassword_listenerNotAssembled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CloudAutoConfiguration.class))
                // 不提供 me.encrypt.password，不提供 StringEncryptor bean
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 无 StringEncryptor bean 时不应装配监听器
                    assertThat(context).doesNotHaveBean(RefreshDecryptListener.class);
                });
    }
}

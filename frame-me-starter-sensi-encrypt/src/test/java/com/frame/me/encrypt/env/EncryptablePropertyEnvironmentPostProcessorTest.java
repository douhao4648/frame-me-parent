package com.frame.me.encrypt.env;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EncryptablePropertyEnvironmentPostProcessor} 单元测试.
 *
 * @author frame-me
 */
class EncryptablePropertyEnvironmentPostProcessorTest {

    private static final String PASSWORD = "test-master-password";

    private final EncryptablePropertyEnvironmentPostProcessor processor =
            new EncryptablePropertyEnvironmentPostProcessor();

    private String encrypt(String plain) {
        StandardPBEStringEncryptor encryptor = JasyptEncryptor.create(
                PASSWORD, EncryptConstant.DEFAULT_ALGORITHM, EncryptConstant.DEFAULT_ITERATIONS);
        return EncryptConstant.DEFAULT_PREFIX + encryptor.encrypt(plain) + EncryptConstant.DEFAULT_SUFFIX;
    }

    private MockEnvironment envWithPassword() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(EncryptConstant.PASSWORD_KEY, PASSWORD);
        return env;
    }

    /**
     * 密文原位解密：读取拿到明文，且不新增任何属性源（不是 addFirst 覆盖模式）.
     */
    @Test
    void decryptsInPlaceWithoutAddingSource() {
        MockEnvironment env = envWithPassword();
        env.getPropertySources().addLast(new MapPropertySource("app",
                Map.of("db.password", encrypt("s3cret"))));
        int sourceCount = env.getPropertySources().size();

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("db.password")).isEqualTo("s3cret");
        assertThat(env.getPropertySources().size()).isEqualTo(sourceCount);
        assertThat(env.getPropertySources().get("app")).isInstanceOf(DecryptedPropertySource.class);
    }

    /**
     * 优先级链不被破坏：更高优先级来源的明文值仍能覆盖加密配置项
     * （addFirst 模式下解密值恒胜，运行时无法紧急覆盖）.
     */
    @Test
    void higherPriorityPlainValueStillOverrides() {
        MockEnvironment env = envWithPassword();
        env.getPropertySources().addLast(new MapPropertySource("app",
                Map.of("db.url", encrypt("jdbc:mysql://prod/db"))));
        env.getPropertySources().addFirst(new MapPropertySource("commandLine",
                Map.of("db.url", "jdbc:mysql://standby/db")));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("db.url")).isEqualTo("jdbc:mysql://standby/db");
    }

    /**
     * 低优先级来源有密文、高优先级来源无该 key 时，仍读到解密值.
     */
    @Test
    void decryptedValueVisibleWhenNoOverride() {
        MockEnvironment env = envWithPassword();
        env.getPropertySources().addFirst(new MapPropertySource("commandLine",
                Map.of("unrelated", "x")));
        env.getPropertySources().addLast(new MapPropertySource("app",
                Map.of("db.url", encrypt("jdbc:mysql://prod/db"))));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("db.url")).isEqualTo("jdbc:mysql://prod/db");
    }

    /**
     * 主密码错误导致解密失败时启动期 fail-fast，而非延迟到首次读取.
     */
    @Test
    void wrongPasswordFailsFastAtStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(EncryptConstant.PASSWORD_KEY, "wrong-password");
        env.getPropertySources().addLast(new MapPropertySource("app",
                Map.of("db.password", encrypt("s3cret"))));

        assertThatThrownBy(() -> processor.postProcessEnvironment(env, null))
                .isInstanceOf(org.jasypt.exceptions.EncryptionOperationNotPossibleException.class);
    }

    /**
     * 未配置主密码：完全跳过，密文原样保留、属性源不替换.
     */
    @Test
    void noPasswordLeavesEverythingUntouched() {
        MockEnvironment env = new MockEnvironment();
        String cipher = encrypt("s3cret");
        env.getPropertySources().addLast(new MapPropertySource("app", Map.of("db.password", cipher)));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("db.password")).isEqualTo(cipher);
        assertThat(env.getPropertySources().get("app")).isInstanceOf(MapPropertySource.class);
    }

    /**
     * 配置中心动态刷新密文后，同一 key 读到新明文而非旧缓存.
     *
     * <p>旧实现缓存键只用 name，密文刷新后命中旧缓存返回旧明文（密钥轮换不生效）；
     * 修复后缓存键含密文，密文变化缓存失效重新解密.</p>
     */
    @Test
    void refreshedCipherReReadsNewPlaintext() {
        MockEnvironment env = envWithPassword();
        Map<String, Object> source = new HashMap<>();
        source.put("db.password", encrypt("old-pass"));
        env.getPropertySources().addLast(new MapPropertySource("app", source));

        processor.postProcessEnvironment(env, null);

        // 首次读取：解密 old-pass
        assertThat(env.getProperty("db.password")).isEqualTo("old-pass");

        // 模拟配置中心刷新：同一 key 换密文
        source.put("db.password", encrypt("new-pass"));

        // 刷新后读取：应得到新明文，而非旧缓存
        assertThat(env.getProperty("db.password")).isEqualTo("new-pass");
    }

    /**
     * 频繁轮换密文时，每次读取均返回当期明文，旧密文条目自然留存.
     *
     * <p>旧版实现走 stale cleanup（removeIf），由于与 computeIfAbsent 无原子性保证已移除；
     * 新版各密文条目由 name@cipher 自然隔离，缓存保留所有版本，配置轮换频率极低，量级可忽略.</p>
     */
    @Test
    void rotatedCipherEvictsStaleCacheEntries() throws Exception {
        MockEnvironment env = envWithPassword();
        Map<String, Object> source = new HashMap<>();
        source.put("db.password", encrypt("pass-1"));
        env.getPropertySources().addLast(new MapPropertySource("app", source));

        processor.postProcessEnvironment(env, null);

        // 多次轮换密文并读取，每次都应得到当期明文
        for (int i = 2; i <= 5; i++) {
            source.put("db.password", encrypt("pass-" + i));
            assertThat(env.getProperty("db.password")).isEqualTo("pass-" + i);
        }

        // 缓存保留所有轮换版本的密文条目（name@cipher 自然隔离，不冲突）
        Object wrapped = env.getPropertySources().get("app");
        java.lang.reflect.Field cacheField = wrapped.getClass().getDeclaredField("decryptedCache");
        cacheField.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) cacheField.get(wrapped);
        assertThat(cache).hasSize(5);
    }
}

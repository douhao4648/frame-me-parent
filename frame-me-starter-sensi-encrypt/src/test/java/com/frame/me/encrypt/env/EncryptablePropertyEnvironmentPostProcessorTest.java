package com.frame.me.encrypt.env;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

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
}

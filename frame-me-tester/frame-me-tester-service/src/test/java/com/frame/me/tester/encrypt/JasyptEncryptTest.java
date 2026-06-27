package com.frame.me.tester.encrypt;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Jasypt 配置解密集成测试.
 *
 * <p>验证 {@code frame-me-starter-sensi-encrypt} 的 {@code EncryptablePropertyEnvironmentPostProcessor}
 * 能在应用启动早期把配置中的 {@code ME(密文)} 解密为明文。主密码通过系统属性
 * {@code me.encrypt.password} 注入（模拟真实环境的环境变量），密文见
 * {@code application-encrypt.yml}。</p>
 *
 * <p>主密码在 static 块中设置——必须早于 Spring 上下文创建（EnvironmentPostProcessor 在启动早期运行）；
 * 配合 {@link DirtiesContext} 强制本类使用全新上下文，避免命中缓存中未解密的上下文。</p>
 */
@SpringBootTest
@ActiveProfiles({"test", "encrypt"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JasyptEncryptTest {

    private static final String PASSWORD_KEY = "me.encrypt.password";

    static {
        System.setProperty(PASSWORD_KEY, "me2026");
    }

    @AfterAll
    static void clearMasterPassword() {
        System.clearProperty(PASSWORD_KEY);
    }

    @Autowired
    private Environment environment;

    /**
     * {@code ME(...)} 密文应被解密为原始明文 "root".
     */
    @Test
    void shouldDecryptEncPlaceholder() {
        assertEquals("root", environment.getProperty("me.demo.secret"));
    }
}

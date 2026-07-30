package com.frame.me.encrypt.cli;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JasyptEncryptCli} 单元测试.
 *
 * @author frame-me
 */
class JasyptEncryptCliTest {

    private static final String PASSWORD = "cli-test-password";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private void capture() {
        out.reset();
        err.reset();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    void restore() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.clearProperty(EncryptConstant.PASSWORD_KEY);
    }

    /**
     * argv 第二参数（主密码）一律拒绝并给出安全指引，防止主密码进 shell history / ps.
     */
    @Test
    void passwordInArgvRejected() {
        capture();
        JasyptEncryptCli.main(new String[]{"plain-text", PASSWORD});

        assertThat(err.toString()).contains("不接受命令行参数");
        assertThat(out.toString()).isBlank();
    }

    /**
     * 系统属性提供主密码：正常输出 ME(密文)，且可用同密码解密还原.
     */
    @Test
    void encryptsWithPasswordFromSystemProperty() {
        System.setProperty(EncryptConstant.PASSWORD_KEY, PASSWORD);
        capture();
        JasyptEncryptCli.main(new String[]{"plain-text"});

        String output = out.toString().trim();
        assertThat(output).startsWith(EncryptConstant.DEFAULT_PREFIX).endsWith(EncryptConstant.DEFAULT_SUFFIX);

        String cipher = output.substring(EncryptConstant.DEFAULT_PREFIX.length(),
                output.length() - EncryptConstant.DEFAULT_SUFFIX.length());
        StandardPBEStringEncryptor encryptor = JasyptEncryptor.create(
                PASSWORD, EncryptConstant.DEFAULT_ALGORITHM, EncryptConstant.DEFAULT_ITERATIONS);
        assertThat(encryptor.decrypt(cipher)).isEqualTo("plain-text");
    }

    /**
     * 全部渠道缺失（无 sysprop、surefire 无 console）→ 报错提示，不输出密文.
     */
    @Test
    void missingPasswordReportsError() {
        capture();
        JasyptEncryptCli.main(new String[]{"plain-text"});

        assertThat(err.toString()).contains("缺少主密码");
        assertThat(out.toString()).isBlank();
    }
}

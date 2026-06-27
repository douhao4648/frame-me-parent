package com.frame.me.encrypt.cli;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

/**
 * 离线密文生成工具.
 *
 * <p>把明文加密成可写进配置文件的 {@code ME(密文)}。主密码与运行时保持一致，<b>不要写进配置文件</b>。</p>
 *
 * <p>用法（任选其一提供主密码，优先级：命令行参数 &gt; 环境变量 &gt; 系统属性）：</p>
 * <pre>
 *   java ... com.frame.me.encrypt.cli.JasyptEncryptCli &lt;明文&gt; &lt;主密码&gt;
 *   ME_ENCRYPT_PASSWORD=xxx java ... JasyptEncryptCli &lt;明文&gt;
 *   java -Dframe.me.encrypt.password=xxx ... JasyptEncryptCli &lt;明文&gt;
 * </pre>
 */
public final class JasyptEncryptCli {

    private JasyptEncryptCli() {
    }

    /**
     * 入口：加密单个明文并打印 {@code ME(密文)}.
     *
     * @param args {@code [明文] [主密码?]}
     */
    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println("用法: JasyptEncryptCli <明文> [主密码]");
            System.err.println("  主密码也可由环境变量 ME_ENCRYPT_PASSWORD 或系统属性 frame.me.encrypt.password 提供");
            return;
        }

        String plaintext = args[0];
        String password = args.length >= 2 ? args[1] : resolvePassword();
        if (password == null || password.isBlank()) {
            System.err.println("缺少主密码：请通过命令行第 2 个参数、环境变量 ME_ENCRYPT_PASSWORD 或系统属性 frame.me.encrypt.password 提供");
            return;
        }

        StandardPBEStringEncryptor encryptor = JasyptEncryptor.create(
                password, EncryptConstant.DEFAULT_ALGORITHM, EncryptConstant.DEFAULT_ITERATIONS);
        String cipher = encryptor.encrypt(plaintext);
        System.out.println(EncryptConstant.DEFAULT_PREFIX + cipher + EncryptConstant.DEFAULT_SUFFIX);
    }

    private static String resolvePassword() {
        String env = System.getenv("ME_ENCRYPT_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getProperty(EncryptConstant.PASSWORD_KEY);
    }
}

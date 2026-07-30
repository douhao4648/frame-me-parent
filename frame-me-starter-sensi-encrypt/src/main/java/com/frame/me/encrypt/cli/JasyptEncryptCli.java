package com.frame.me.encrypt.cli;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

import java.io.Console;

/**
 * 离线密文生成工具.
 *
 * <p>把明文加密成可写进配置文件的 {@code ME(密文)}。主密码与运行时保持一致，<b>不要写进配置文件</b>。</p>
 *
 * <p>主密码<b>不接受命令行参数</b>（argv 会进 shell history 且 {@code ps} 可见），
 * 按以下优先级解析：环境变量 {@code ME_ENCRYPT_PASSWORD} &gt; 系统属性
 * {@code me.encrypt.password} &gt; 控制台交互输入（无回显）：</p>
 * <pre>
 *   ME_ENCRYPT_PASSWORD=xxx java ... JasyptEncryptCli &lt;明文&gt;
 *   java -Dme.encrypt.password=xxx ... JasyptEncryptCli &lt;明文&gt;
 *   java ... JasyptEncryptCli &lt;明文&gt;          # 交互式输入主密码
 * </pre>
 *
 * @author frame-me
 */
public final class JasyptEncryptCli {

    private JasyptEncryptCli() {
    }

    /**
     * 入口：加密单个明文并打印 {@code ME(密文)}.
     *
     * @param args {@code [明文]}（主密码一律不走 argv，见类 Javadoc）
     */
    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println("用法: JasyptEncryptCli <明文>");
            System.err.println("  主密码由环境变量 ME_ENCRYPT_PASSWORD、系统属性 me.encrypt.password 或控制台交互输入提供");
            return;
        }
        if (args.length >= 2) {
            System.err.println("主密码不接受命令行参数（会进 shell history 且 ps 可见），"
                    + "请改用环境变量 ME_ENCRYPT_PASSWORD、系统属性 me.encrypt.password 或控制台交互输入");
            return;
        }

        String password = resolvePassword();
        if (password == null || password.isBlank()) {
            System.err.println("缺少主密码：请通过环境变量 ME_ENCRYPT_PASSWORD、系统属性 me.encrypt.password 提供，"
                    + "或在支持控制台的终端交互输入");
            return;
        }

        StandardPBEStringEncryptor encryptor = JasyptEncryptor.create(
                password, EncryptConstant.DEFAULT_ALGORITHM, EncryptConstant.DEFAULT_ITERATIONS);
        String cipher = encryptor.encrypt(args[0]);
        System.out.println(EncryptConstant.DEFAULT_PREFIX + cipher + EncryptConstant.DEFAULT_SUFFIX);
    }

    /**
     * 解析主密码：环境变量 &gt; 系统属性 &gt; 控制台交互输入（无回显）.
     *
     * @return 主密码，全部渠道缺失时返回 {@code null}
     */
    static String resolvePassword() {
        String env = System.getenv("ME_ENCRYPT_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String sysprop = System.getProperty(EncryptConstant.PASSWORD_KEY);
        if (sysprop != null && !sysprop.isBlank()) {
            return sysprop;
        }
        Console console = System.console();
        if (console != null) {
            char[] input = console.readPassword("主密码: ");
            if (input != null && input.length > 0) {
                return new String(input);
            }
        }
        return null;
    }
}

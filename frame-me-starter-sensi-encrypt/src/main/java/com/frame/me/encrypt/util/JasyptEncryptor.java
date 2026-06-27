package com.frame.me.encrypt.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;

/**
 * Jasypt 字符串加解密器构建工具.
 *
 * <p>统一封装 {@link StandardPBEStringEncryptor} 的构建参数，供配置解密
 * {@link com.frame.me.encrypt.env.EncryptablePropertyEnvironmentPostProcessor}
 * 与离线密文生成 {@link com.frame.me.encrypt.cli.JasyptEncryptCli} 复用，
 * 避免两处算法参数不一致导致解密失败。</p>
 */
public final class JasyptEncryptor {

    private JasyptEncryptor() {
    }

    /**
     * 构建一个 PBE 字符串加解密器.
     *
     * <p>默认采用 {@code base64} 输出，搭配随机盐与随机 IV；同一份明文每次加密结果不同，
     * 但均可用相同主密码解密。AES 类算法（如 {@code PBEWITHHMACSHA512ANDAES_256}）必须提供 IV。</p>
     *
     * @param password   主密码
     * @param algorithm  PBE 算法名
     * @param iterations 密钥迭代次数
     * @return 加解密器
     */
    public static StandardPBEStringEncryptor create(String password, String algorithm, int iterations) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm(algorithm);
        encryptor.setKeyObtentionIterations(iterations);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setStringOutputType("base64");
        return encryptor;
    }
}

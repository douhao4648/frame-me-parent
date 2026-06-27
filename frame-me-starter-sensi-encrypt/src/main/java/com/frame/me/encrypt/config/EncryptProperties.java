package com.frame.me.encrypt.config;

import com.frame.me.encrypt.EncryptConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置加密模块属性.
 *
 * <p>绑定前缀 {@code me.encrypt}，供 {@link EncryptAutoConfiguration} 构建业务用的
 * {@code StringEncryptor} Bean。与配置解密处理器
 * {@link com.frame.me.encrypt.env.EncryptablePropertyEnvironmentPostProcessor} 共用同一主密码。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.encrypt")
public class EncryptProperties {

    /**
     * 主密码，运行时由环境变量 {@code ME_ENCRYPT_PASSWORD} / 启动参数注入，<b>不落配置文件</b>.
     */
    private String password;

    /**
     * PBE 算法名，默认 {@code PBEWITHHMACSHA512ANDAES_256}.
     */
    private String algorithm = EncryptConstant.DEFAULT_ALGORITHM;

    /**
     * 密钥迭代次数，默认 1000.
     */
    private int iterations = EncryptConstant.DEFAULT_ITERATIONS;
}

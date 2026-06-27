package com.frame.me.encrypt.config;

import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置加密模块自动装配：暴露业务可注入的 {@link StringEncryptor} Bean.
 *
 * <p>区别于配置解密处理器
 * {@link com.frame.me.encrypt.env.EncryptablePropertyEnvironmentPostProcessor}（只作用于配置属性），
 * 本 Bean 供业务代码对自身数据加解密（如落库前加密、解密第三方密钥），复用同一主密码
 * {@code frame.me.encrypt.password} 与同一套算法参数。</p>
 *
 * <p>仅在配置了主密码时注册，未配置则不暴露 Bean，对现有应用零影响。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StandardPBEStringEncryptor.class)
@ConditionalOnProperty(prefix = "frame.me.encrypt", name = "password")
@EnableConfigurationProperties(EncryptProperties.class)
public class EncryptAutoConfiguration {

    /**
     * 业务加解密器，与配置解密共用算法参数，保证密文互通.
     *
     * @param properties 加密属性
     * @return {@link StringEncryptor}
     */
    @Bean
    @ConditionalOnMissingBean
    public StringEncryptor stringEncryptor(EncryptProperties properties) {
        return JasyptEncryptor.create(properties.getPassword(), properties.getAlgorithm(), properties.getIterations());
    }
}

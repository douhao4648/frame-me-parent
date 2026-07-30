package com.frame.me.encrypt.env;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.util.JasyptEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置项解密处理器：解密形如 {@code ME(密文)} 的属性值.
 *
 * <p>在 Spring Boot 启动早期、配置文件加载之后运行，扫描 {@link ConfigurableEnvironment}
 * 中所有可枚举属性源，将含 {@code ME(...)} 密文的属性源<b>原位替换</b>为
 * {@link DecryptedPropertySource} 包装器（读取时解密），使下游（数据源、Redis 等）拿到明文。
 * 原位替换不改变优先级链：命令行 / JVM -D / 环境变量等更高优先级来源仍可覆盖加密配置项。</p>
 *
 * <p>主密码从 {@code me.encrypt.password} 读取（兼容环境变量 {@code ME_ENCRYPT_PASSWORD}、
 * JVM 系统属性），<b>不写入任何配置文件</b>。主密码缺失时直接跳过，对没有密文的应用零影响。</p>
 *
 * <p>注册方式为 {@code META-INF/spring.factories} 的
 * {@code org.springframework.boot.EnvironmentPostProcessor} 键——{@link EnvironmentPostProcessor}
 * 早于自动装配，无法通过 {@code AutoConfiguration.imports} 注册。</p>
 *
 * <p>跳过系统环境变量源：Spring Boot 3.5+ 对系统环境属性源做了性能优化，会绕过属性源包装，
 * 且密文放进系统环境变量并无意义，故不处理。</p>
 */
public class EncryptablePropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String password = environment.getProperty(EncryptConstant.PASSWORD_KEY);
        if (password == null || password.isBlank()) {
            return;
        }

        String algorithm = environment.getProperty(EncryptConstant.ALGORITHM_KEY, EncryptConstant.DEFAULT_ALGORITHM);
        int iterations = environment.getProperty(EncryptConstant.ITERATIONS_KEY, Integer.class, EncryptConstant.DEFAULT_ITERATIONS);
        String prefix = environment.getProperty(EncryptConstant.PREFIX_KEY, EncryptConstant.DEFAULT_PREFIX);
        String suffix = environment.getProperty(EncryptConstant.SUFFIX_KEY, EncryptConstant.DEFAULT_SUFFIX);

        StandardPBEStringEncryptor encryptor = JasyptEncryptor.create(password, algorithm, iterations);

        // 先收集再替换，避免边遍历边修改属性源列表
        List<DecryptedPropertySource> wrappers = new ArrayList<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof SystemEnvironmentPropertySource) {
                continue;
            }
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            DecryptedPropertySource wrapper = new DecryptedPropertySource(enumerable, encryptor, prefix, suffix);
            if (wrapper.hasEncryptedProperties()) {
                wrappers.add(wrapper);
            }
        }
        for (DecryptedPropertySource wrapper : wrappers) {
            environment.getPropertySources().replace(wrapper.getName(), wrapper);
        }
        // 启动期 fail-fast：预解密全部密文，密文损坏/主密码错误在启动即暴露，而非延迟到首次读取
        wrappers.forEach(DecryptedPropertySource::decryptAll);
    }

    @Override
    public int getOrder() {
        // 配置文件（ConfigData）加载之后再运行，确保能扫描到 application.yml 中的密文
        return Ordered.LOWEST_PRECEDENCE;
    }
}

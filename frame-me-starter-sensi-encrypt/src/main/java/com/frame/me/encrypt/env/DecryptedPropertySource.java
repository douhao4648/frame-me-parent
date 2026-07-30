package com.frame.me.encrypt.env;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.core.env.EnumerablePropertySource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原位解密属性源包装器：读取时对 {@code ME(密文)} 值即时解密（结果缓存），
 * 不改变属性源在优先级链中的位置.
 *
 * <p>相比「解密结果 addFirst 到最高优先级」的做法，包装模式保持了
 * Spring Boot 的优先级语义：命令行 / JVM -D / 环境变量等更高优先级来源
 * 仍能覆盖加密配置项（生产紧急切换场景的刚需）。</p>
 *
 * @author frame-me
 */
class DecryptedPropertySource extends EnumerablePropertySource<Object> {

    private final EnumerablePropertySource<?> delegate;
    private final StandardPBEStringEncryptor encryptor;
    private final String prefix;
    private final String suffix;

    /**
     * 已解密值缓存：StandardPBEStringEncryptor 解密有 PBKDF2 迭代开销，按 key 缓存一次.
     */
    private final Map<String, Object> decryptedCache = new ConcurrentHashMap<>();

    DecryptedPropertySource(EnumerablePropertySource<?> delegate,
                            StandardPBEStringEncryptor encryptor, String prefix, String suffix) {
        super(delegate.getName(), delegate.getSource());
        this.delegate = delegate;
        this.encryptor = encryptor;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    @Override
    public String[] getPropertyNames() {
        return delegate.getPropertyNames();
    }

    @Override
    public Object getProperty(String name) {
        Object value = delegate.getProperty(name);
        if (value instanceof String text && isEncrypted(text)) {
            return decryptedCache.computeIfAbsent(name,
                    k -> encryptor.decrypt(text.substring(prefix.length(), text.length() - suffix.length())));
        }
        return value;
    }

    /**
     * 是否含有密文属性（决定是否替换原属性源）.
     */
    boolean hasEncryptedProperties() {
        for (String name : delegate.getPropertyNames()) {
            if (delegate.getProperty(name) instanceof String text && isEncrypted(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 预解密全部密文：让密文损坏 / 主密码错误在启动期 fail-fast，
     * 而不是延迟到首次读取.
     */
    void decryptAll() {
        for (String name : delegate.getPropertyNames()) {
            getProperty(name);
        }
    }

    private boolean isEncrypted(String text) {
        return text.length() >= prefix.length() + suffix.length()
                && text.startsWith(prefix) && text.endsWith(suffix);
    }
}

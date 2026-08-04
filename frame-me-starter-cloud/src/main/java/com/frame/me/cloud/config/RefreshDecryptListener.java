package com.frame.me.cloud.config;

import com.frame.me.encrypt.EncryptConstant;
import com.frame.me.encrypt.env.DecryptedPropertySource;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置中心无关的刷新解密监听器.
 *
 * <p>当任意配置中心（Nacos / Apollo / Consul 等）触发 Spring Cloud 的
 * {@link EnvironmentChangeEvent} 后，{@code ContextRefresher} 会用从配置中心新拉取的
 * 原始 {@link PropertySource} 替换 environment 中的旧源。新源里的 {@code ME(密文)}
 * 是未解密的裸字符串，而 {@code frame-me-starter-sensi-encrypt} 的
 * {@code EncryptablePropertyEnvironmentPostProcessor} 只在启动期跑一次，不会重跑——
 * 本监听器在刷新后补做一次解密包装，使 environment 恢复解密状态.</p>
 *
 * <p>配置中心无关：不绑定具体配置中心，任何走 Spring Cloud 刷新体系（发布
 * {@link EnvironmentChangeEvent}）的配置中心都自动被覆盖。解密参数（prefix / suffix）
 * 与 sensi-encrypt 共用同一套配置源 {@code me.encrypt.*}，保证密文互通.</p>
 *
 * <p>幂等：已是 {@link DecryptedPropertySource} 的源跳过，防重复包装.</p>
 *
 * <p>短窗口期：{@link EnvironmentChangeEvent} 触发到本监听器跑完之间，environment 里的
 * 密文短暂裸露。期间 {@code @RefreshScope} bean 重建若读密文会踩坑——约束：敏感配置 bean
 * 不用 {@code @RefreshScope}.</p>
 *
 * @author frame-me
 */
public class RefreshDecryptListener implements ApplicationListener<EnvironmentChangeEvent> {

    private static final Logger log = LoggerFactory.getLogger(RefreshDecryptListener.class);

    private final ConfigurableEnvironment environment;
    private final StringEncryptor encryptor;

    public RefreshDecryptListener(ConfigurableEnvironment environment, StringEncryptor encryptor) {
        this.environment = environment;
        this.encryptor = encryptor;
    }

    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        String prefix = environment.getProperty(EncryptConstant.PREFIX_KEY, EncryptConstant.DEFAULT_PREFIX);
        String suffix = environment.getProperty(EncryptConstant.SUFFIX_KEY, EncryptConstant.DEFAULT_SUFFIX);

        // 先收集再替换，避免边遍历边修改属性源列表
        List<DecryptedPropertySource> wrappers = new ArrayList<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            // 幂等：已是解密包装器的跳过，防重复包装
            if (source instanceof DecryptedPropertySource) {
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
            log.debug("刷新后重新包装解密属性源: {}", wrapper.getName());
        }
        if (!wrappers.isEmpty()) {
            log.info("配置刷新后完成解密包装，共 {} 个属性源", wrappers.size());
        }
    }
}

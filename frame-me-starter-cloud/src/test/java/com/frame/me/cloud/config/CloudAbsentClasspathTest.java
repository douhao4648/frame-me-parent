package com.frame.me.cloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sensi-encrypt 缺席时的装配测试.
 *
 * <p>{@code frame-me-starter-sensi-encrypt} 是 cloud 的 optional 依赖，消费方未引入时
 * {@link CloudAutoConfiguration.RefreshDecryptAutoConfiguration} 必须在 ASM 元数据评估阶段
 * 整体退避（不加载 {@code DecryptedPropertySource} / {@code StringEncryptor}、不抛 NCDFE），
 * cloud 自身不装配任何解密相关 bean.</p>
 *
 * <p>本模块 test classpath 恒有 sensi-encrypt（optional 只阻断传递），故用
 * {@link FilteredClassLoader} 屏蔽 {@code com.frame.me.encrypt} 包来模拟缺席.</p>
 *
 * @author frame-me
 */
class CloudAbsentClasspathTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader("com.frame.me.encrypt"));

    /**
     * sensi-encrypt 缺席：上下文正常启动（不抛 NCDFE），不装配任何刷新解密相关 bean.
     */
    @Test
    void sensiEncryptAbsent_contextStartsNoDecryptBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RefreshDecryptListener.class);
        });
    }
}

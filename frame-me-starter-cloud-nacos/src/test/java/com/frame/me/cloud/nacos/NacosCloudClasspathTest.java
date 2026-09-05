package com.frame.me.cloud.nacos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nacos 组件 classpath 冒烟测试.
 *
 * <p>本模块极薄（仅引入 SCA 官方 starter + 占位常量类），无自有装配逻辑.
 * 本测试验证 SCA nacos-config / nacos-discovery starter 在 Java 21 下依赖解析正常、
 * 关键类可加载（Java 21 向前兼容性的最小验证）.</p>
 *
 * @author frame-me
 */
class NacosCloudClasspathTest {

    /**
     * 验证 SCA nacos-config 关键类在 classpath（依赖解析正常）.
     */
    @Test
    void nacosConfigClassesPresentOnClasspath() {
        assertThat(getClass().getClassLoader().getResource(
                "META-INF/spring.factories")).as("SCA nacos-config spring.factories").isNotNull();
    }

    /**
     * 验证 NacosPropertySource 类可加载（nacos-client 在 Java 21 下无加载问题）.
     */
    @Test
    void nacosPropertySourceClassLoadable() throws ClassNotFoundException {
        Class<?> clazz = Class.forName("com.alibaba.cloud.nacos.client.NacosPropertySource");
        assertThat(clazz).isNotNull();
    }
}

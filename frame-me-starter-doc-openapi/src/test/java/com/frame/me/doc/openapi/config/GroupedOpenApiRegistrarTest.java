package com.frame.me.doc.openapi.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotationMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GroupedOpenApiRegistrar} 注册行为测试.
 *
 * @author frame-me
 */
class GroupedOpenApiRegistrarTest {

    /**
     * 配置重复 group 名时：两个 bean 都注册（bean 名带下标后缀区分），
     * 且不抛异常——warn 仅为提示，不阻断启动.
     */
    @Test
    void duplicateGroupNamesBothRegisteredWithSuffix() {
        StandardEnvironment env = new StandardEnvironment();
        java.util.Properties props = new java.util.Properties();
        props.setProperty("me.swagger.groups[0].name", "dup");
        props.setProperty("me.swagger.groups[0].paths-to-match[0]", "/a/**");
        props.setProperty("me.swagger.groups[1].name", "dup");
        props.setProperty("me.swagger.groups[1].paths-to-match[0]", "/b/**");
        env.getPropertySources().addFirst(new org.springframework.core.env.PropertiesPropertySource("test", props));

        GroupedOpenApiRegistrar registrar = new GroupedOpenApiRegistrar();
        registrar.setEnvironment(env);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registrar.registerBeanDefinitions(Mockito.mock(AnnotationMetadata.class), registry);

        // 两个同名 group 都注册：第一个用 groupedOpenApi_dup，第二个因 bean 名已存在改用后缀
        String[] names = registry.getBeanDefinitionNames();
        long matched = java.util.Arrays.stream(names).filter(n -> n.startsWith("groupedOpenApi_dup")).count();
        assertThat(matched).isEqualTo(2);
    }

    /**
     * 未配置分组时注册默认分组.
     */
    @Test
    void defaultGroupRegisteredWhenNoConfig() {
        StandardEnvironment env = new StandardEnvironment();

        GroupedOpenApiRegistrar registrar = new GroupedOpenApiRegistrar();
        registrar.setEnvironment(env);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registrar.registerBeanDefinitions(Mockito.mock(AnnotationMetadata.class), registry);

        assertThat(registry.getBeanDefinitionNames())
                .anyMatch(n -> n.equals("groupedOpenApi_default"));
    }
}

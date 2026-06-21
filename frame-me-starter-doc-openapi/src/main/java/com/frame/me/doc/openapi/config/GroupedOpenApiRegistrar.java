package com.frame.me.doc.openapi.config;

import com.frame.me.doc.openapi.config.DocOpenApiProperties.GroupProperties;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 {@link DocOpenApiProperties} 动态注册 {@link GroupedOpenApi} bean.
 *
 * <p>未配置分组时，默认注册一个名为 {@code default}、匹配所有路径的分组。
 */
public class GroupedOpenApiRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    /**
     * 默认分组.
     */
    private static final GroupProperties DEFAULT_GROUP = new GroupProperties();

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        List<GroupProperties> groups = loadGroups();
        for (int i = 0; i < groups.size(); i++) {
            GroupProperties group = groups.get(i);
            String beanName = "groupedOpenApi_" + group.getName();
            // 避免同名 bean 冲突，使用下标后缀
            if (registry.containsBeanDefinition(beanName)) {
                beanName = beanName + "_" + i;
            }
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(GroupedOpenApi.class, () -> GroupedOpenApi.builder().group(group.getName()).pathsToMatch(group.getPathsToMatch().toArray(new String[0])).build());
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        }
    }

    /**
     * 从配置中加载分组列表.
     *
     * @return 分组列表
     */
    private List<GroupProperties> loadGroups() {
        List<GroupProperties> groups = Binder.get(environment).bind("frame.me.swagger.groups", Bindable.listOf(GroupProperties.class)).orElse(new ArrayList<>());
        if (groups.isEmpty()) {
            groups.add(DEFAULT_GROUP);
        }
        return groups;
    }
}

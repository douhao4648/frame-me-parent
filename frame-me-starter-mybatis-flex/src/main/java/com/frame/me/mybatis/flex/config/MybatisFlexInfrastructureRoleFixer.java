package com.frame.me.mybatis.flex.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 修复 MyBatis-Flex 内部配置类的 BeanPostProcessor 警告.
 *
 * <p>将 {@code MultiDataSourceAutoConfiguration} 与 {@code MybatisFlexProperties}
 * 标记为基础设施 Bean，避免 Spring 在 BeanPostProcessor 实例化阶段打印 WARN。</p>
 */
@Slf4j
public class MybatisFlexInfrastructureRoleFixer implements BeanFactoryPostProcessor {

    private static final String[] MYBATIS_FLEX_INFRA_BEANS = {
            "com.mybatisflex.spring.boot.v4.MultiDataSourceAutoConfiguration",
            "mybatis-flex-com.mybatisflex.spring.boot.v4.MybatisFlexProperties"
    };

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (String beanName : MYBATIS_FLEX_INFRA_BEANS) {
            if (beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
                log.debug("Set ROLE_INFRASTRUCTURE for MyBatis-Flex bean: {}", beanName);
            }
        }
    }

}

package com.frame.me.cache.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 修复 JetCache 内部配置类的 BeanPostProcessor 警告.
 *
 * <p>将 {@code JetCacheProxyConfiguration} 与 {@code CommonConfiguration} 标记为基础设施 Bean，
 * 避免 Spring 在 BeanPostProcessor 实例化阶段打印 WARN。</p>
 */
@Slf4j
public class JetCacheInfrastructureRoleFixer implements BeanFactoryPostProcessor {

    private static final String[] JETCACHE_INFRA_BEANS = {
            "com.alicp.jetcache.anno.config.JetCacheProxyConfiguration",
            "com.alicp.jetcache.anno.config.CommonConfiguration"
    };

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (String beanName : JETCACHE_INFRA_BEANS) {
            if (beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
                log.debug("Set ROLE_INFRASTRUCTURE for JetCache bean: {}", beanName);
            }
        }
    }

}

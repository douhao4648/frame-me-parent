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
        int hit = 0;
        for (String beanName : JETCACHE_INFRA_BEANS) {
            if (beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
                log.debug("Set ROLE_INFRASTRUCTURE for JetCache bean: {}", beanName);
                hit++;
            }
        }
        // 全部未命中：JetCache 版本升级可能改了 bean 名/包路径，fixer 静默失效，WARN 提示排查
        if (hit == 0) {
            log.warn("JetCacheInfrastructureRoleFixer 未命中任何 JetCache bean（{}），"
                            + "可能 JetCache 版本升级导致 bean 名变化，ROLE_INFRASTRUCTURE 修复未生效，"
                            + "Spring 启动期 BeanPostProcessor WARN 可能重新出现，请检查 bean 名是否需更新",
                    java.util.Arrays.toString(JETCACHE_INFRA_BEANS));
        }
    }

}

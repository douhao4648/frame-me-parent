package com.frame.me.dynamic.ds.config;

import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.frame.me.dynamic.ds.provider.MeDynamicDataSourceProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * frame-me 多数据源 Starter 自动配置.
 *
 * <p>本配置类在 baomidou dynamic-datasource 自动配置之前装配，
 * 注册 {@link MeDynamicDataSourceProvider} 用于根据 {@code spring.datasource.*} 创建默认 master 数据源。
 * 通过 {@link Order#HIGHEST_PRECEDENCE} 确保本 Provider 最先加载，
 * 从而当 dynamic-datasource 中也显式配置了 {@code master} 时，
 * 后加载的 YmlDynamicDataSourceProvider 可以覆盖本 Provider 创建的默认 master。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration")
@ConditionalOnProperty(prefix = "me.dynamic-datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.datasource.dynamic", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration.class)
public class DynamicDataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DynamicDataSourceProvider.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public DynamicDataSourceProvider frameMeDynamicDataSourceProvider(DefaultDataSourceCreator dataSourceCreator,
                                                                     ConfigurableEnvironment environment) {
        return new MeDynamicDataSourceProvider(dataSourceCreator, environment);
    }
}

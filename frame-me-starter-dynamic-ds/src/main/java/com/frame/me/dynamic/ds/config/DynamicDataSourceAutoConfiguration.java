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
 *
 * <p><b>两个 {@code @ConditionalOnProperty} 是 AND 关系，各有作用</b>：
 * <ul>
 *   <li>{@code me.dynamic-datasource.enabled}（matchIfMissing=true）：本模块总开关，
 *       业务方可单独关闭本 Provider 而保留 baomidou 原生装配。</li>
 *   <li>{@code spring.datasource.dynamic.enabled}（matchIfMissing=true）：跟随 baomidou
 *       官方开关。baomidou 官方该开关<b>默认 false</b>（不配不装配），
 *       本模块用 matchIfMissing=true 翻转为默认装配，使「引入 starter 即创建 master 数据源」
 *       的体验与 baomidou 原生「需显式 spring.datasource.dynamic.enabled=true」不同。
 *       业务方若要用 baomidou 原生方式（不通过本 Provider），需同时设
 *       {@code me.dynamic-datasource.enabled=false} 关本模块。</li>
 * </ul>
 * <p>两个开关均不命中时本配置退避，避免 baomidou 关闭时本 Provider 空转装配.</p>
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
    public DynamicDataSourceProvider meDynamicDataSourceProvider(DefaultDataSourceCreator dataSourceCreator,
                                                                 ConfigurableEnvironment environment) {
        return new MeDynamicDataSourceProvider(dataSourceCreator, environment);
    }
}

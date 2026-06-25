package com.frame.me.base.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * 声明式 HTTP 接口客户端（{@code @ImportHttpServices}）自动配置.
 *
 * <p>为所有 RestClient 分组的代理工厂注册 {@link QueryObjectArgumentResolver}，
 * 使裸查询对象（POJO）参数能被展开为 URL query 参数。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
public class HttpServiceClientAutoConfiguration {

    @Bean
    RestClientHttpServiceGroupConfigurer queryObjectArgumentResolverConfigurer() {
        QueryObjectArgumentResolver resolver = new QueryObjectArgumentResolver();
        return groups -> groups.forEachProxyFactory((group, factoryBuilder) -> factoryBuilder.customArgumentResolver(resolver));
    }
}

package com.frame.me.base.config;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 池化 RestClient.Builder 自动配置.
 *
 * <p>当 classpath 存在 Apache HttpClient 5 时，使用连接池创建默认 {@link RestClient.Builder}，
 * 替代 Spring Boot 默认的 {@code SimpleClientHttpRequestFactory}。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({HttpClientBuilder.class, HttpComponentsClientHttpRequestFactory.class})
@AutoConfigureBefore(RestClientAutoConfiguration.class)
@EnableConfigurationProperties(PoolingRestClientProperties.class)
public class PoolingRestClientAutoConfiguration {

    /**
     * 创建基于 HttpClient 5 连接池的 RestClient.Builder.
     *
     * @param properties 连接池配置属性
     * @return 池化 RestClient.Builder
     */
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder restClientBuilder(PoolingRestClientProperties properties) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(
                        HttpClientBuilder.create()
                                .setConnectionManager(connectionManager)
                                .build()));
    }
}

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
import org.springframework.context.annotation.Scope;
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
     * 共享连接池请求工厂（singleton）.
     *
     * <p>昂贵的资源（连接池 / HttpClient 的 socket 与线程）只创建一份、全局共享，
     * 由容器管理生命周期（实现 {@code DisposableBean}，关闭时释放连接）.</p>
     *
     * @param properties 连接池配置属性
     * @return 池化请求工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpComponentsClientHttpRequestFactory poolingRestClientRequestFactory(PoolingRestClientProperties properties) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());
        // 连接级配置（HC5.4+ 从 RequestConfig/管理器直设挪到 ConnectionConfig）：
        // connectTimeout 连接建立超时；validateAfterInactivity 复用前校验连接是否半关闭（空闲连接驱逐）
        org.apache.hc.client5.http.config.ConnectionConfig connectionConfig =
                org.apache.hc.client5.http.config.ConnectionConfig.custom()
                        .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getConnectTimeout().toMillis()))
                        .setValidateAfterInactivity(org.apache.hc.core5.util.TimeValue.ofMilliseconds(properties.getValidateAfterInactivity()))
                        .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        // 请求级超时（HttpClient5 用 org.apache.hc.core5.util.Timeout，非 java.time.Duration）
        org.apache.hc.core5.util.Timeout responseTimeout = org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getResponseTimeout().toMillis());
        org.apache.hc.core5.util.Timeout requestTimeout = org.apache.hc.core5.util.Timeout.ofMilliseconds(properties.getConnectionRequestTimeout().toMillis());
        org.apache.hc.client5.http.config.RequestConfig requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                .setResponseTimeout(responseTimeout)
                .setConnectionRequestTimeout(requestTimeout)
                .setConnectionKeepAlive(org.apache.hc.core5.util.TimeValue.ofMilliseconds(properties.getKeepAlive().toMillis()))
                .build();
        return new HttpComponentsClientHttpRequestFactory(
                HttpClientBuilder.create()
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(requestConfig)
                        .build());
    }

    /**
     * 创建基于共享连接池的 RestClient.Builder.
     *
     * <p>prototype 作用域（对齐 Boot 的 {@code RestClientAutoConfiguration}）：
     * Builder 是可变对象，调用方会叠加各自的 defaultHeader 等配置，单例共享会互相污染；
     * 每个注入点拿到独立 Builder，底层引用同一个 singleton 连接池工厂.
     * 注意：连接池不能跟着 Builder 做成 prototype——prototype Bean 容器不调用销毁方法，
     * 每注入点一个池既丢失共享语义又泄漏连接.</p>
     *
     * @param requestFactory 共享连接池请求工厂
     * @return 池化 RestClient.Builder
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder restClientBuilder(HttpComponentsClientHttpRequestFactory poolingRestClientRequestFactory) {
        return RestClient.builder().requestFactory(poolingRestClientRequestFactory);
    }
}

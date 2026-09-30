package com.frame.me.base.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.IdleConnectionEvictor;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.autoconfigure.HttpClientsProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.time.Duration;

/**
 * 池化 HTTP 客户端自动配置.
 *
 * <p>当 classpath 存在 Apache HttpClient 5 时，注册共享 {@link PoolingHttpClientConnectionManager}
 * 连接池（单例，空闲/过期连接驱逐由独立的 {@link IdleConnectionEvictor} Bean 承担），并通过
 * {@link ClientHttpRequestFactoryBuilder} 让所有 HTTP 调用方式复用该池：注入 {@code RestClient.Builder}、
 * {@code @ImportHttpServices} 声明式接口、{@code RestTemplateBuilder} → {@code RestTemplate}。</p>
 *
 * <p>超时优先级：{@code spring.http.serviceclient.<group>.read-timeout}（group 级）→
 * {@code spring.http.clients.read-timeout}（全局级）→ {@code me.restclient.pool.response-timeout}
 * （池化默认）。连接池始终共享，超时每次按 settings 叠加。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({HttpClientBuilder.class, HttpComponentsClientHttpRequestFactory.class})
@AutoConfigureBefore(
        value = RestClientAutoConfiguration.class,
        // Boot 4 的 ImperativeHttpClientAutoConfiguration 也注册 ClientHttpRequestFactoryBuilder Bean，
        // 双方均为 @ConditionalOnMissingBean——必须先于它装配才能让我们的池化 builder 生效。
        // 用 name 字符串引用，避免 starter 与 Boot 内部自动配置类编译耦合
        name = "org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration")
@EnableConfigurationProperties(PoolingRestClientProperties.class)
public class PoolingRestClientAutoConfiguration {

    /**
     * 共享连接池管理器（singleton）.
     *
     * <p>昂贵的资源（连接池的 socket 与线程）只创建一份、全局共享，由容器管理生命周期
     * （实现 {@code DisposableBean}，关闭时释放连接）。后台驱逐线程只起一次，
     * 见 {@link #poolingIdleConnectionEvictor}。</p>
     *
     * <p>connectTimeout 优先级：{@code spring.http.clients.connect-timeout}（全局）→
     * {@code me.restclient.pool.connect-timeout}（池化默认）。group 级 connect-timeout
     * 无法 per-request 覆盖（ConnectionConfig 绑共享 ConnectionManager），统一用全局值。</p>
     *
     * @param properties                    连接池配置属性
     * @param httpClientsPropertiesProvider 全局 HTTP 客户端配置（{@code spring.http.clients.*}）
     * @return 共享连接池管理器
     */
    @Bean
    public PoolingHttpClientConnectionManager poolingConnectionManager(
            PoolingRestClientProperties properties,
            ObjectProvider<HttpClientsProperties> httpClientsPropertiesProvider) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());
        // connectTimeout：全局 spring.http.clients.connect-timeout 覆盖池化默认
        Duration connectTimeout = properties.getConnectTimeout();
        HttpClientsProperties httpClientsProperties = httpClientsPropertiesProvider.getIfAvailable();
        if (httpClientsProperties != null && httpClientsProperties.getConnectTimeout() != null) {
            connectTimeout = httpClientsProperties.getConnectTimeout();
        }
        // 连接级配置（HC5.4+ 从 RequestConfig/管理器直设挪到 ConnectionConfig）：
        // connectTimeout 连接建立超时；validateAfterInactivity 复用前校验连接是否半关闭（空闲连接驱逐）
        ConnectionConfig connectionConfig =
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setValidateAfterInactivity(TimeValue.ofMilliseconds(properties.getValidateAfterInactivity()))
                        .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        return connectionManager;
    }

    /**
     * 共享池的空闲/过期连接驱逐任务（singleton，随容器启停）.
     *
     * <p>各 HttpClient 均 {@code setConnectionManagerShared(true)}，HC5 因此对共享 manager
     * 跳过内建 {@code IdleConnectionEvictor} 的创建（其接线条件是 {@code !connManagerShared}），
     * builder 上的 {@code evictExpiredConnections()}/{@code evictIdleConnections()} 实为死配置。
     * 故为共享池独立注册驱逐线程：每 10 秒扫描，驱逐过期连接与空闲超 30 秒的连接；
     * {@code destroyMethod="shutdown"} 保证容器关闭时线程退出，池所有权仍归共享 manager。</p>
     *
     * @param poolingConnectionManager 共享连接池管理器
     * @return 空闲连接驱逐器
     */
    @Bean(destroyMethod = "shutdown")
    public IdleConnectionEvictor poolingIdleConnectionEvictor(
            PoolingHttpClientConnectionManager poolingConnectionManager) {
        IdleConnectionEvictor evictor = new IdleConnectionEvictor(poolingConnectionManager,
                TimeValue.ofSeconds(10), TimeValue.ofSeconds(30));
        evictor.start();
        return evictor;
    }

    /**
     * 共享连接池请求工厂（singleton）.
     *
     * <p>注入共享 {@link PoolingHttpClientConnectionManager}，供直接注入 {@code ClientHttpRequestFactory}
     * 的代码使用。超时走 {@code me.restclient.pool.*} 默认值。</p>
     *
     * <p>空闲/过期连接驱逐由 {@link #poolingIdleConnectionEvictor} 统一承担（shared manager
     * 下 HC5 不会在 client 内接线驱逐）；{@code setConnectionManagerShared(true)}
     * 保证本工厂 close 时不连带关闭共享池。</p>
     *
     * @param poolingConnectionManager 共享连接池管理器
     * @param properties               连接池配置属性
     * @return 池化请求工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpComponentsClientHttpRequestFactory poolingRestClientRequestFactory(
            PoolingHttpClientConnectionManager poolingConnectionManager,
            PoolingRestClientProperties properties) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getResponseTimeout().toMillis()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getConnectionRequestTimeout().toMillis()))
                .setConnectionKeepAlive(TimeValue.ofMilliseconds(properties.getKeepAlive().toMillis()))
                .build();
        return new HttpComponentsClientHttpRequestFactory(
                HttpClientBuilder.create()
                        .setConnectionManager(poolingConnectionManager)
                        .setConnectionManagerShared(true)
                        .setDefaultRequestConfig(requestConfig)
                        .build());
    }

    /**
     * 池化 {@link ClientHttpRequestFactoryBuilder}（singleton）.
     *
     * <p>所有 HTTP 调用方式复用共享连接池：{@code RestClient.Builder}、{@code @ImportHttpServices}
     * 声明式接口、{@code RestTemplateBuilder} → {@code RestTemplate}。</p>
     *
     * <p>每次 {@code build(settings)} 新建轻量 {@link HttpClient}（连接池共享，只是 wrapper），
     * 按传入 settings 叠加超时：group 级 {@code spring.http.serviceclient.<group>.*} →
     * 全局 {@code spring.http.clients.*} → 池化默认 {@code me.restclient.pool.*}。
     * 传入 settings 已由 Boot 4 的 {@code HttpClientSettingsPropertyMapper} 合并 group 与全局，
     * 为 {@code null} 的字段 fallback 到池化默认。</p>
     *
     * @param poolingConnectionManager 共享连接池管理器
     * @param properties               连接池配置属性
     * @return 池化请求工厂 builder
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientHttpRequestFactoryBuilder<HttpComponentsClientHttpRequestFactory> poolingClientHttpRequestFactoryBuilder(
            PoolingHttpClientConnectionManager poolingConnectionManager,
            PoolingRestClientProperties properties) {
        return settings -> {
            // 读取/响应超时：group 级 spring.http.serviceclient.<group>.read-timeout →
            // 全局 spring.http.clients.read-timeout → 池化默认 me.restclient.pool.response-timeout
            Duration readTimeout = properties.getResponseTimeout();
            if (settings != null && settings.readTimeout() != null) {
                readTimeout = settings.readTimeout();
            }
            // 注意：connectTimeout 是连接级配置（ConnectionConfig），绑到共享 ConnectionManager，
            // group 级 connect-timeout 无法 per-request 覆盖，统一用 me.restclient.pool.connect-timeout
            RequestConfig requestConfig = RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getConnectionRequestTimeout().toMillis()))
                    .setConnectionKeepAlive(TimeValue.ofMilliseconds(properties.getKeepAlive().toMillis()))
                    .build();
            // 新建轻量 HttpClient，复用共享 ConnectionManager（连接池共享）；
            // shared 标记保证本 wrapper 被 close 时不连带关闭共享池。
            // 驱逐统一由 poolingIdleConnectionEvictor Bean 承担（shared manager 下 HC5 不会接线内建驱逐）
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                    .setConnectionManager(poolingConnectionManager)
                    .setConnectionManagerShared(true)
                    .setDefaultRequestConfig(requestConfig);
            // 必须响应 settings.redirects()：HC5 默认跟随重定向，不处理则
            // TestRestTemplate.withRedirects(DONT_FOLLOW) 等调用静默失效
            // （302 被跟随成 200/外部 DNS 解析），FOLLOW_WHEN_POSSIBLE/FOLLOW 即 HC5 默认行为
            if (settings != null && settings.redirects() == HttpRedirects.DONT_FOLLOW) {
                httpClientBuilder.disableRedirectHandling();
            }
            return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
        };
    }
}

package com.frame.me.base.config;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.pool.PoolStats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.HttpServiceProxyRegistry;
import org.springframework.web.service.registry.ImportHttpServices;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PoolingRestClientAutoConfiguration} 测试.
 *
 * <p>观测手段：对 {@link PoolingHttpClientConnectionManager#getTotalStats()} 的 leased 计数
 * 在调用飞行途中断言，即可证明"某个调用用了哪个池"。用 JDK 内置 {@link HttpServer}
 * 起本地桩服务，{@code /slow} 端点用双 latch 挂起请求以便中途观测。</p>
 *
 * <p>覆盖三层配置的叠加优先级：池化默认 {@code me.restclient.pool.*} →
 * 全局 {@code spring.http.clients.*} → 分组 {@code spring.http.serviceclient.<group>.*}。</p>
 *
 * @author frame-me
 */
class PoolingRestClientAutoConfigurationTest {

    private static HttpServer server;

    private static String baseUrl;

    /** /slow 端点：handler 进入时 countDown，供测试确认"请求已在飞行中". */
    private static volatile CountDownLatch entered;

    /** /slow 端点：测试观测完后 countDown 放行响应. */
    private static volatile CountDownLatch release;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PoolingRestClientAutoConfiguration.class,
                    HttpClientAutoConfiguration.class,
                    // 故意带上 Boot 默认装配，验证我们的池化 builder 先注册、默认的让位
                    ImperativeHttpClientAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    RestTemplateAutoConfiguration.class,
                    HttpServiceClientPropertiesAutoConfiguration.class,
                    HttpServiceClientAutoConfiguration.class));

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ok", exchange -> respond(exchange, "ok"));
        server.createContext("/lazy", exchange -> {
            sleep(1000);
            respond(exchange, "lazy");
        });
        server.createContext("/slow", exchange -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, "slow");
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void resetLatches() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    /**
     * 观测"某个调用用了哪个池"：RestClient / RestTemplate / @ImportHttpServices 三种调用形式
     * 飞行途中共享池 leased 计数 +1；手工 RestClient.create() 绕过容器，leased 恒 0.
     */
    @Test
    void allCallFormsUseTheOneSharedPool() {
        contextRunner.withUserConfiguration(PlainGroupConfig.class)
                .withPropertyValues("spring.http.serviceclient.plain.base-url=" + baseUrl)
                .run(context -> {
                    // Boot 默认 builder 让位：容器里只有我们的池化 builder
                    assertThat(context.getBeanNamesForType(ClientHttpRequestFactoryBuilder.class))
                            .containsExactly("poolingClientHttpRequestFactoryBuilder");
                    assertThat(context).hasSingleBean(PoolingHttpClientConnectionManager.class);
                    PoolingHttpClientConnectionManager manager =
                            context.getBean(PoolingHttpClientConnectionManager.class);

                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    try {
                        // 形式一：注入 RestClient.Builder 构建的 RestClient
                        RestClient restClient = context.getBean(RestClient.Builder.class)
                                .baseUrl(baseUrl).build();
                        assertLeasedDuringSlowCall(manager, executor,
                                () -> restClient.get().uri("/slow").retrieve().toBodilessEntity());

                        // 形式二：RestTemplateBuilder 构建的 RestTemplate
                        assertLeasedDuringSlowCall(manager, executor,
                                () -> context.getBean(RestTemplateBuilder.class)
                                        .build().getForEntity(baseUrl + "/slow", Void.class));

                        // 形式三：@ImportHttpServices 声明式接口
                        DemoApi proxy = context.getBean(HttpServiceProxyRegistry.class)
                                .getClient("plain", DemoApi.class);
                        assertLeasedDuringSlowCall(manager, executor, proxy::slow);

                        // 对照组：手工 RestClient.create() 不走容器 Builder，自己内部建池，
                        // 我们的共享池 leased 恒为 0——证明观测手段能区分"用了哪个池"
                        resetLatches();
                        RestClient manual = RestClient.create(baseUrl);
                        Future<?> future = executor.submit(
                                () -> manual.get().uri("/slow").retrieve().toBodilessEntity());
                        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                        assertThat(manager.getTotalStats().getLeased()).isZero();
                        release.countDown();
                        future.get(5, TimeUnit.SECONDS);
                    } finally {
                        executor.shutdownNow();
                    }
                });
    }

    /**
     * 池化配置 me.restclient.pool.* 绑定到共享 ConnectionManager.
     *
     * <p>maxTotal/maxPerRoute 有公开 getter；connectTimeout/validateAfterInactivity 在 HC5 中
     * 没有 getter（存在私有 connectionConfigResolver 里），用反射读回精确值——不依赖网络环境，
     * 比连黑洞地址测耗时可靠。HC5 升级若改内部结构此测试会显式失败，提示重新审视.</p>
     */
    @Test
    void poolPropertiesBoundToConnectionManager() {
        contextRunner.withPropertyValues(
                "me.restclient.pool.max-total=7",
                "me.restclient.pool.max-per-route=3",
                "me.restclient.pool.connect-timeout=2s",
                "me.restclient.pool.validate-after-inactivity=500"
        ).run(context -> {
            PoolingHttpClientConnectionManager manager =
                    context.getBean(PoolingHttpClientConnectionManager.class);
            assertThat(manager.getMaxTotal()).isEqualTo(7);
            assertThat(manager.getDefaultMaxPerRoute()).isEqualTo(3);
            ConnectionConfig connectionConfig = defaultConnectionConfig(manager);
            assertThat(connectionConfig.getConnectTimeout().toMilliseconds()).isEqualTo(2000);
            assertThat(connectionConfig.getValidateAfterInactivity().toMilliseconds()).isEqualTo(500);
        });
    }

    /**
     * 全局 spring.http.clients.connect-timeout 覆盖池化默认 connect-timeout.
     */
    @Test
    void globalConnectTimeoutOverridesPoolDefault() {
        contextRunner.withPropertyValues(
                "me.restclient.pool.connect-timeout=2s",
                "spring.http.clients.connect-timeout=9s"
        ).run(context -> assertThat(defaultConnectionConfig(
                        context.getBean(PoolingHttpClientConnectionManager.class))
                .getConnectTimeout().toMilliseconds()).isEqualTo(9000));
    }

    /**
     * 全局 spring.http.clients.read-timeout 经 RestClient.Builder 生效（覆盖池化默认）.
     * 桩端点延迟 1s，read-timeout 300ms 必超时；未配全局时用池化默认 10s 则成功.
     */
    @Test
    void globalReadTimeoutAppliedToRestClientBuilder() {
        contextRunner.withPropertyValues(
                "me.restclient.pool.response-timeout=10s",
                "spring.http.clients.read-timeout=300ms"
        ).run(context -> {
            RestClient restClient = context.getBean(RestClient.Builder.class)
                    .baseUrl(baseUrl).build();
            assertThatThrownBy(() -> restClient.get().uri("/lazy").retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
        });

        contextRunner.withPropertyValues("me.restclient.pool.response-timeout=10s")
                .run(context -> {
                    RestClient restClient = context.getBean(RestClient.Builder.class)
                            .baseUrl(baseUrl).build();
                    // 无全局 read-timeout：池化默认 10s > 端点延迟 1s，调用成功
                    restClient.get().uri("/lazy").retrieve().toBodilessEntity();
                });
    }

    /**
     * builder.build(settings) 叠加语义：settings 为 null 或字段为 null 时回落池化默认，
     * settings.readTimeout 显式传入时覆盖池化默认.
     */
    @Test
    @SuppressWarnings("unchecked")
    void settingsReadTimeoutOverridesPoolDefault() {
        contextRunner.withPropertyValues("me.restclient.pool.response-timeout=10s")
                .run(context -> {
                    ClientHttpRequestFactoryBuilder<ClientHttpRequestFactory> builder =
                            (ClientHttpRequestFactoryBuilder<ClientHttpRequestFactory>)
                                    context.getBean(ClientHttpRequestFactoryBuilder.class);

                    // settings.readTimeout=200ms 覆盖池化默认 10s → 超时
                    ClientHttpRequestFactory overridden =
                            builder.build(HttpClientSettings.defaults()
                                    .withReadTimeout(Duration.ofMillis(200)));
                    RestClient fast = RestClient.builder()
                            .requestFactory(overridden).baseUrl(baseUrl).build();
                    assertThatThrownBy(() -> fast.get().uri("/lazy").retrieve().toBodilessEntity())
                            .isInstanceOf(ResourceAccessException.class);

                    // settings 为 null → 池化默认 10s → 成功
                    ClientHttpRequestFactory fallback = builder.build(null);
                    RestClient slow = RestClient.builder()
                            .requestFactory(fallback).baseUrl(baseUrl).build();
                    slow.get().uri("/lazy").retrieve().toBodilessEntity();
                });
    }

    /**
     * 分组配置 spring.http.serviceclient.<group>.*：demo 组 read-timeout=200ms 超时，
     * plain 组未配则回落池化默认成功——组间隔离，互不影响.
     */
    @Test
    void groupReadTimeoutIsolatedPerGroup() {
        contextRunner.withUserConfiguration(DemoGroupConfig.class, PlainGroupConfig.class)
                .withPropertyValues(
                        "spring.http.serviceclient.demo.base-url=" + baseUrl,
                        "spring.http.serviceclient.demo.read-timeout=200ms",
                        "spring.http.serviceclient.plain.base-url=" + baseUrl)
                .run(context -> {
                    HttpServiceProxyRegistry registry = context.getBean(HttpServiceProxyRegistry.class);
                    DemoApi demo = registry.getClient("demo", DemoApi.class);
                    DemoApi plain = registry.getClient("plain", DemoApi.class);

                    assertThatThrownBy(demo::lazy).isInstanceOf(ResourceAccessException.class);
                    assertThat(plain.lazy()).isEqualTo("lazy");
                });
    }

    /**
     * 三层优先级闭环：group > global > pool。全局 read-timeout=300ms 时，
     * demo 组显式 10s 覆盖全局（慢端点成功），plain 组未配则继承全局（慢端点超时）——
     * 即 Boot 的 settings.orElse(global) 缺口填充 + 我们的 settings.orElse(pool) 回落.
     */
    @Test
    void groupOverridesGlobalAndGlobalFillsGroupGap() {
        contextRunner.withUserConfiguration(DemoGroupConfig.class, PlainGroupConfig.class)
                .withPropertyValues(
                        "spring.http.clients.read-timeout=300ms",
                        "spring.http.serviceclient.demo.base-url=" + baseUrl,
                        "spring.http.serviceclient.demo.read-timeout=10s",
                        "spring.http.serviceclient.plain.base-url=" + baseUrl)
                .run(context -> {
                    HttpServiceProxyRegistry registry = context.getBean(HttpServiceProxyRegistry.class);
                    // group 显式值覆盖全局 300ms → 1s 慢端点成功
                    assertThat(registry.getClient("demo", DemoApi.class).lazy()).isEqualTo("lazy");
                    // group 未配 → 继承全局 300ms → 1s 慢端点超时
                    assertThatThrownBy(() -> registry.getClient("plain", DemoApi.class).lazy())
                            .isInstanceOf(ResourceAccessException.class);
                });
    }

    /**
     * 在后台线程发起 /slow 调用，请求飞行途中断言共享池 leased>=1，随后放行并等其完成.
     */
    private void assertLeasedDuringSlowCall(PoolingHttpClientConnectionManager manager,
                                            ExecutorService executor, ThrowingCall call) throws Exception {
        resetLatches();
        Future<?> future = executor.submit(() -> {
            try {
                call.run();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        PoolStats stats = manager.getTotalStats();
        assertThat(stats.getLeased()).as("调用飞行途中共享池应有租出连接").isGreaterThanOrEqualTo(1);
        release.countDown();
        future.get(5, TimeUnit.SECONDS);
    }

    /**
     * 反射读回共享池的默认 {@link ConnectionConfig}（HC5 无公开 getter，
     * {@code setDefaultConnectionConfig} 内部存为 route -> config 的 resolver）.
     */
    @SuppressWarnings("unchecked")
    private static ConnectionConfig defaultConnectionConfig(PoolingHttpClientConnectionManager manager) {
        Resolver<HttpRoute, ConnectionConfig> resolver = (Resolver<HttpRoute, ConnectionConfig>)
                ReflectionTestUtils.getField(manager, "connectionConfigResolver");
        assertThat(resolver).isNotNull();
        return resolver.resolve(null);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    /**
     * 测试用声明式 HTTP 接口.
     */
    interface DemoApi {

        @GetExchange("/slow")
        String slow();

        @GetExchange("/lazy")
        String lazy();
    }

    @ImportHttpServices(group = "demo", types = DemoApi.class)
    static class DemoGroupConfig {
    }

    @ImportHttpServices(group = "plain", types = DemoApi.class)
    static class PlainGroupConfig {
    }
}

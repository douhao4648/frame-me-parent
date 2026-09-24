package com.frame.me.auth.config;

import com.frame.me.auth.core.NoOpAuthUserResolver;
import com.frame.me.auth.core.TrustedHeaderAuthUserResolver;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.auth.spi.IServiceInstanceProbe;
import com.frame.me.base.limit.InMemoryLoginRateLimiter;
import com.frame.me.base.limit.LoginRateLimiter;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthAutoConfiguration} 装配测试：验证信任头解析器三态开关、fail-closed、业务接管与 Web 应用类型条件.
 *
 * @author frame-me
 */
class AuthAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthAutoConfiguration.class))
            .withUserConfiguration(StubInfraConfig.class);

    @Test
    void customPasswordEncoderTakesPrecedence() {
        PasswordEncoder custom = org.mockito.Mockito.mock(PasswordEncoder.class);
        runner.withPropertyValues("me.auth.trusted-header.enabled=false")
                .withBean(PasswordEncoder.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                    assertThat(context.getBean(PasswordEncoder.class)).isSameAs(custom);
                    assertThat(context).hasSingleBean(com.frame.me.auth.core.AuthUserAuthenticator.class);
                });
    }

    /**
     * 非 Servlet Web 应用整体退避：RequestMappingHandlerMapping 不存在，
     * 缺此条件 authFilter 装配会失败并拖垮启动.
     */
    @Test
    void backsOffInNonWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuthAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("authFilter");
                    assertThat(context).doesNotHaveBean(IAuthUserResolver.class);
                });
    }

    /**
     * fail-closed：无任何 IAuthUserResolver 实现且未显式开启 Header 解析器时，
     * 启动直接失败并给出指引，而不是静默退化为不安全的默认行为.
     */
    @Test
    void failsFastWhenNoResolver() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("未找到 IAuthUserResolver 实现");
        });
    }

    /**
     * 显式开启后装配 Header 解析器，认证过滤器正常注册.
     */
    @Test
    void trustedHeaderEnabledBySwitch() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IAuthUserResolver.class))
                            .isInstanceOf(TrustedHeaderAuthUserResolver.class);
                    assertThat(context).hasBean("authFilter");
                });
    }

    /**
     * 显式 enabled=false：装配空操作解析器（不解析任何身份），启动正常、认证过滤器注册；
     * 与不配（unset）的 fail-closed 区分.
     */
    @Test
    void noOpResolverWhenExplicitlyDisabled() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IAuthUserResolver.class))
                            .isInstanceOf(NoOpAuthUserResolver.class);
                    assertThat(context).hasBean("authFilter");
                });
    }

    /**
     * trusted-header 配置绑定：enabled 三态（默认 null=unset）与 warn-enabled（默认 true）
     * 正确绑定到 {@link AuthProperties}.
     */
    @Test
    void trustedHeaderPropertiesBound() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=false")
                .run(context -> {
                    AuthProperties props = context.getBean(AuthProperties.class);
                    assertThat(props.getTrustedHeader().getEnabled()).isFalse();
                    assertThat(props.getTrustedHeader().getWarnEnabled()).isTrue();
                });
        runner.withPropertyValues("me.auth.trusted-header.enabled=true",
                        "me.auth.trusted-header.warn-enabled=false")
                .run(context -> {
                    AuthProperties props = context.getBean(AuthProperties.class);
                    assertThat(props.getTrustedHeader().getEnabled()).isTrue();
                    assertThat(props.getTrustedHeader().getWarnEnabled()).isFalse();
                });
    }

    /**
     * 业务自定义 IAuthUserResolver 时，即使开关开启，Header 解析器也让位.
     */
    @Test
    void customResolverTakesPrecedence() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=true")
                .withUserConfiguration(CustomResolverConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IAuthUserResolver.class);
                    assertThat(context.getBean(IAuthUserResolver.class))
                            .isInstanceOf(CustomResolver.class);
                });
    }

    /**
     * 容器中存在无关的通用 Predicate bean 时，不会被当作服务名探针，
     * 传播拦截器仍使用默认的 LoadBalancer 探针，装配不冲突.
     */
    @Test
    void unrelatedPredicateBeanNotHijackedAsProbe() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=true")
                .withUserConfiguration(UnrelatedPredicateConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("authPropagationInterceptor");
                    assertThat(context).hasSingleBean(IServiceInstanceProbe.class);
                    assertThat(context.getBean(IServiceInstanceProbe.class))
                            .isNotSameAs(context.getBean("unrelatedPredicate"));
                });
    }

    /**
     * 业务自定义 IServiceInstanceProbe 时覆盖默认的 LoadBalancer 探针.
     */
    @Test
    void customProbeOverridesDefault() {
        IServiceInstanceProbe customProbe = host -> true;
        runner.withPropertyValues("me.auth.trusted-header.enabled=true")
                .withBean(IServiceInstanceProbe.class, () -> customProbe)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IServiceInstanceProbe.class);
                    assertThat(context.getBean(IServiceInstanceProbe.class)).isSameAs(customProbe);
                });
    }

    /**
     * 默认装配内存版登录限流器.
     */
    @Test
    void loginRateLimiterDefaultsToInMemory() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LoginRateLimiter.class);
                    assertThat(context.getBean(LoginRateLimiter.class))
                            .isInstanceOf(InMemoryLoginRateLimiter.class);
                });
    }

    /**
     * me.auth.login-rate-limit.enabled=false 时不装配任何登录限流器.
     */
    @Test
    void loginRateLimiterDisabledBySwitch() {
        runner.withPropertyValues("me.auth.trusted-header.enabled=true",
                        "me.auth.login-rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LoginRateLimiter.class);
                });
    }

    /**
     * JVM 层机制验证：缺 op-audit 时，对配置类反射枚举方法（getDeclaredMethods，
     * 真实 Web 应用中 CommonAnnotation/Scheduled 后处理器的标准动作）不得抛
     * NoClassDefFoundError.
     *
     * <p>必须用 child-first 加载器让配置类真正由"缺包的加载器"定义；
     * 普通 FilteredClassLoader 双亲委派会让配置类回落到父加载器，掩盖问题.</p>
     */
    @Test
    void configClassSurvivesMethodReflectionWithoutOpAudit() throws Exception {
        ClassLoader classLoader = new ChildFirstNoAuditClassLoader(getClass().getClassLoader());
        Class<?> configClass = Class.forName("com.frame.me.auth.config.AuthAutoConfiguration", false, classLoader);
        org.assertj.core.api.Assertions.assertThatCode(configClass::getDeclaredMethods)
                .doesNotThrowAnyException();
    }

    /**
     * op-audit 为 optional 依赖：消费方剔除审计 JAR 后配置类仍须正常装配，
     * 不装配审计操作人 Bean，也不得抛 NoClassDefFoundError.
     *
     * <p>配置类显式经 child-first 缺包加载器加载，模拟真实消费方 classpath 缺包场景
     * （直接传 Class 字面量或双亲委派 FilteredClassLoader 都会走测试类加载器，掩盖失败）.</p>
     */
    @Test
    void loadsWithoutOpAuditJar() throws Exception {
        ClassLoader classLoader = new ChildFirstNoAuditClassLoader(getClass().getClassLoader());
        Class<?> configClass = Class.forName("com.frame.me.auth.config.AuthAutoConfiguration", false, classLoader);
        runner.withClassLoader(classLoader)
                .withConfiguration(AutoConfigurations.of(configClass))
                .withPropertyValues("me.auth.trusted-header.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("auditAuthOperatorSupplier");
                });
    }

    /**
     * 测试基础设施：authFilter 依赖的 MVC 与错误响应写入器 stub.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubInfraConfig {

        @Bean
        RequestMappingHandlerMapping requestMappingHandlerMapping() {
            return new RequestMappingHandlerMapping();
        }

        @Bean
        IFilterErrorResponseWriter filterErrorResponseWriter() {
            return (HttpServletResponse response, ResultCode resultCode, String message) -> {
            };
        }
    }

    /**
     * 无关的通用 Predicate bean 配置：模拟业务或第三方库注册的 Predicate.
     */
    @Configuration(proxyBeanMethods = false)
    static class UnrelatedPredicateConfig {

        @Bean
        java.util.function.Predicate<String> unrelatedPredicate() {
            return host -> true;
        }
    }

    /**
     * 业务自定义解析器配置.
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomResolverConfig {

        @Bean
        IAuthUserResolver customAuthUserResolver() {
            return new CustomResolver();
        }
    }

    /**
     * 业务自定义解析器.
     */
    static class CustomResolver implements IAuthUserResolver {

        @Override
        public User resolve(HttpServletRequest request) {
            return null;
        }
    }

    /**
     * 模拟"消费方剔除 op-audit"的 child-first 加载器：auth 包类由本加载器定义，
     * op-audit 包一律视为不存在，其余委托父加载器.
     */
    static final class ChildFirstNoAuditClassLoader extends ClassLoader {

        ChildFirstNoAuditClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (name.startsWith("com.frame.me.op.audit")) {
                        throw new ClassNotFoundException(name);
                    }
                    if (name.startsWith("com.frame.me.auth")) {
                        loaded = defineFromParent(name);
                    }
                    else {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private Class<?> defineFromParent(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (java.io.InputStream in = getParent().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            catch (java.io.IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}

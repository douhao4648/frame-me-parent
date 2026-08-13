package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.satoken.advice.SaTokenExceptionAdvice;
import com.frame.me.auth.satoken.core.SaTokenAuthService;
import com.frame.me.auth.satoken.core.SaTokenAuthUserResolver;
import com.frame.me.auth.satoken.core.SaTokenRuleEvaluator;
import com.frame.me.auth.satoken.permission.ConfigStpInterface;
import com.frame.me.auth.satoken.web.SaTokenAuthController;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.limit.LoginRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sa-Token 认证自动配置.
 *
 * <p>接管 {@link IAuthService} / {@link IAuthUserResolver}，并注册配置版
 * {@link StpInterface} 与 {@link SaInterceptor}（路径规则 + {@code @SaCheck*} 注解鉴权）。
 * sa-token 原生 {@code SaTokenConfig} 由官方 starter 的 {@code SaBeanRegister}
 * 绑定 {@code sa-token.*} 配置路径提供，本模块不再声明。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.sa-token", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SaTokenAuthProperties.class)
@AutoConfigureBefore(name = "com.frame.me.auth.config.AuthAutoConfiguration")
public class SaTokenAuthAutoConfiguration {

    /**
     * Sa-Token 认证服务，接管 {@link IAuthService}.
     *
     * <p>SuppressWarnings：{@code IAuthUserDetailsService} 由业务工程实现，本模块内
     * 无 Bean，IDEA 的自动注入检查属误报（运行时由业务模块提供实现）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(IAuthService.class)
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public IAuthService saTokenAuthService(IAuthUserDetailsService userDetailsService,
                                           SaTokenAuthProperties properties) {
        log.info("SaTokenAuthService initialized");
        return new SaTokenAuthService(userDetailsService, properties);
    }

    /**
     * Sa-Token 当前用户解析器，接管 {@link IAuthUserResolver}.
     */
    @Bean
    @ConditionalOnMissingBean(IAuthUserResolver.class)
    public IAuthUserResolver saTokenAuthUserResolver(IAuthService authService) {
        log.info("SaTokenAuthUserResolver initialized");
        return new SaTokenAuthUserResolver(authService);
    }

    /**
     * 默认认证接口（登录/登出/刷新/当前用户/管理员强制登出）.
     *
     * <p>SuppressWarnings：{@code AuthProperties} 由 frame-me-starter-auth 的
     * {@code AuthAutoConfiguration} 通过 {@code @EnableConfigurationProperties} 注册，
     * IDEA 跨模块索引不到该 Bean，自动注入检查属误报。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public SaTokenAuthController saTokenAuthController(IAuthService authService,
                                                       ObjectProvider<AuthProperties> authProperties,
                                                       ObjectProvider<LoginRateLimiter> loginRateLimiter) {
        return new SaTokenAuthController(authService, authProperties, loginRateLimiter);
    }

    /**
     * sa-token 异常到 401/403 语义的映射.
     */
    @Bean
    @ConditionalOnMissingBean
    public SaTokenExceptionAdvice saTokenExceptionAdvice() {
        return new SaTokenExceptionAdvice();
    }

    /**
     * 配置版权限数据源（sa-token 原生 RBAC）.
     *
     * <p>业务声明任意 {@link StpInterface} Bean 即接管（本默认实现退避）；
     * 接入数据库的实现应自行做缓存——sa-token 官方明确权限数据缓存是实现方责任。</p>
     */
    @Bean
    @ConditionalOnMissingBean(StpInterface.class)
    public StpInterface configStpInterface(SaTokenAuthProperties properties) {
        return new ConfigStpInterface(properties);
    }

    /**
     * 注册 {@link SaInterceptor}：让 {@code @SaCheck*} 注解生效，并按
     * {@code me.auth.sa-token.rules} 逐条执行路径规则校验.
     *
     * <p>规则表达式在装配期预解析（非法表达式直接启动失败，而非运行期才暴露）。
     * 规则为空时仍注册拦截器，保证注解鉴权可用；可通过
     * {@code me.auth.sa-token.authorization.enabled=false} 整体关闭 sa-token 鉴权能力。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.auth.sa-token.authorization", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer saTokenInterceptorConfigurer(SaTokenAuthProperties properties) {
        Map<String, SaTokenRuleEvaluator.Rule> parsedRules = new LinkedHashMap<>();
        properties.getRules().forEach((pattern, expression) ->
                parsedRules.put(pattern, SaTokenRuleEvaluator.parse(expression)));
        // 装配期解析账号体系：SaManager.getStpLogic 不存在则自动创建并注册——
        // 非默认体系（如 SSO 的 sso）由此在启动期完成注册，@SaCheck*(type=...) 的
        // SaManager.getStpLogic(type, false) 查找才不会抛「未能找到对应 StpLogic」
        StpLogic stpLogic = SaManager.getStpLogic(properties.getLogicType());
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(new SaInterceptor(auth -> {
                            // 上下文不可用直接放行。
                            // 场景：management 配置独立端口时，actuator 跑在 child context，其 RequestMappingHandlerMapping
                            // 沿祖先链拾取本 MappedInterceptor（见 AbstractHandlerMapping.detectMappedInterceptors →
                            // BeanFactoryUtils.beansOfTypeIncludingAncestors），但 child servlet 容器不注册
                            // SaTokenContextFilterForJakartaServlet（parent 的 @Bean Filter 不进 child），导致 SaTokenContext
                            // ThreadLocal 为空。isValid() 调 getModelBoxOrNull() 返回 null 不抛异常，安全判断后放行。
                            // actuator 安全由独立端口网络隔离 + 默认仅暴露 health 保障，不纳入业务鉴权链。
                            // 反观 management 与主端口同 context 时，actuator 走独立的 WebMvcEndpointHandlerMapping，
                            // 不拾取本 MappedInterceptor，SaInterceptor 不执行，不存在此问题。
                            if (!SaHolder.getContext().isValid()) {
                                return;
                            }
                            // CORS 预检请求（OPTIONS）带 Origin 头时跳过 sa-token 规则校验，避免预检被鉴权拦截返回 401/403。
                            // 与 AuthFilter 对齐：无 Origin 的 OPTIONS 非真预检，仍走正常鉴权链，防绕过。
                            if ("OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())
                                    && SaHolder.getRequest().getHeader("Origin") != null) {
                                return;
                            }
                            parsedRules.forEach((pattern, rule) ->
                                    SaRouter.match(pattern).check(() -> SaTokenRuleEvaluator.check(rule, stpLogic)));
                        }))
                        .addPathPatterns("/**");
            }
        };
    }
}

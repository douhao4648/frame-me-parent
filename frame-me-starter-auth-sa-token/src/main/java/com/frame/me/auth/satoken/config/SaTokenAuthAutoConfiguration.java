package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpInterface;
import com.frame.me.auth.satoken.advice.SaTokenExceptionAdvice;
import com.frame.me.auth.satoken.core.SaTokenAuthService;
import com.frame.me.auth.satoken.core.SaTokenAuthUserResolver;
import com.frame.me.auth.satoken.core.SaTokenRuleEvaluator;
import com.frame.me.auth.satoken.permission.ConfigStpInterface;
import com.frame.me.auth.satoken.web.SaTokenAuthController;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.spi.IAuthUserResolver;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
    public IAuthService saTokenAuthService(IAuthUserDetailsService userDetailsService) {
        log.info("SaTokenAuthService initialized");
        return new SaTokenAuthService(userDetailsService);
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
     * 默认认证接口（登录/登出/刷新/当前用户）.
     */
    @Bean
    @ConditionalOnMissingBean
    public SaTokenAuthController saTokenAuthController(IAuthService authService) {
        return new SaTokenAuthController(authService);
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
     * 规则为空时仍注册拦截器，保证注解鉴权可用。</p>
     */
    @Bean
    public WebMvcConfigurer saTokenInterceptorConfigurer(SaTokenAuthProperties properties) {
        Map<String, SaTokenRuleEvaluator.Rule> parsedRules = new LinkedHashMap<>();
        properties.getRules().forEach((pattern, expression) ->
                parsedRules.put(pattern, SaTokenRuleEvaluator.parse(expression)));
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(new SaInterceptor(auth -> parsedRules.forEach((pattern, rule) ->
                        SaRouter.match(pattern).check(() -> SaTokenRuleEvaluator.check(rule)))))
                        .addPathPatterns("/**");
            }
        };
    }
}

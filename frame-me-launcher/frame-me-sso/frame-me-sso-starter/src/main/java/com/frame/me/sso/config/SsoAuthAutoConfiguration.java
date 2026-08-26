package com.frame.me.sso.config;

import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.sso.auth.SsoAuthController;
import com.frame.me.sso.auth.SsoAuthService;
import com.frame.me.sso.auth.SsoAuthUserDetailsService;
import com.frame.me.sso.auth.SsoCallbackController;
import com.frame.me.sso.auth.SsoLogoutEventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

/**
 * SSO RP 登录端点自动配置.
 *
 * <p>装配 {@link SsoAuthController} + {@link SsoAuthService}，使下游引本 starter 后
 * 即开箱获得 {@code POST /api/auth/sso-login} 端点（code 换本地会话）与
 * {@link SsoCallbackController} 的回调落地两端点（hash 落地页 + 服务端 Cookie 会话回调）；
 * 并在下游未自定义 {@link IAuthUserDetailsService} 时兜底装配
 * {@link SsoAuthUserDetailsService}（RP 无本地用户表场景的快照 miss 回源重建）。</p>
 *
 * <p><b>守卫</b>：
 * <ul>
 *   <li>{@code @ConditionalOnClass(IAuthService)}：仅当下游引入认证底座
 *       （frame-me-starter-auth）时装配，纯传输层使用场景不强制拉起</li>
 *   <li>{@code @ConditionalOnProperty(me.sso.client.enabled)}：与
 *       {@link SsoClientAutoConfiguration} 同开关，下游可关掉整个 SSO 客户端</li>
 * </ul>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(IAuthService.class)
@ConditionalOnProperty(prefix = "me.sso.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({SsoAuthService.class, SsoAuthController.class, SsoCallbackController.class, SsoLogoutEventListener.class})
public class SsoAuthAutoConfiguration {

    /**
     * RP 兜底用户详情服务：仅当下游未自定义 {@link IAuthUserDetailsService} 时装配，
     * 有本地用户表的下游实现自己接口后本 Bean 自动退让.
     */
    @Bean
    @ConditionalOnMissingBean(IAuthUserDetailsService.class)
    public SsoAuthUserDetailsService ssoAuthUserDetailsService(@Lazy SsoAuthService ssoAuthService) {
        return new SsoAuthUserDetailsService(ssoAuthService);
    }
}

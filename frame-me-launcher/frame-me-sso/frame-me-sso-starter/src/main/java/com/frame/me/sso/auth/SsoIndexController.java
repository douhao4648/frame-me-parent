package com.frame.me.sso.auth;

import com.frame.me.auth.annotation.Anonymous;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 回调落地页控制器（RP 下游通用）.
 *
 * <p>{@code GET /index} 返回内置的 {@code sso/index.html}：SSO authorize 的回调
 * （redirectUri 配为本端点，如 {@code http://your-app/index}）带 {@code code}/{@code state}
 * 落在该页，页面 JS 校验 state（站内相对路径，否则回落 {@code /}）后携带 code 重定向到
 * 目标地址，由目标页（SPA 路由）调 {@code POST /sso-login} 换本地会话。
 * 匿名端点（{@link Anonymous}），与 {@link SsoAuthController#ssoLogin} 同口径。</p>
 *
 * <p><b>为何不用 {@code static/} 静态资源</b>：静态index.html 会把访问路径钉死在
 * {@code /index.html} 并劫持所有下游的 {@code /} welcome page（与下游自有静态资源按
 * classpath 顺序互踩），且静态资源不吃 {@code @Anonymous} 注解、需各下游手工配
 * whitelist——控制器换 {@code /index} 干净路径 + 匿名随 starter 分发，零配置。</p>
 *
 * @author frame-me
 */
@RestController
public class SsoIndexController {

    private static final Resource INDEX_HTML = new ClassPathResource("sso/index.html");

    /**
     * SSO 回调落地页（匿名，text/html）.
     *
     * @return index.html 页面资源
     */
    @Anonymous
    @GetMapping(value = "/index", produces = MediaType.TEXT_HTML_VALUE)
    public Resource index() {
        return INDEX_HTML;
    }
}

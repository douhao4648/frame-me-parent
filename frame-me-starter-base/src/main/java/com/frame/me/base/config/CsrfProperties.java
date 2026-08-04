package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CSRF 防护配置属性.
 *
 * <p>基于 Origin/Referer 头校验的轻量级 CSRF 防护，适用于 Token 认证（JWT/sa-token）的 REST API。
 * 仅对状态变更方法（POST/PUT/PATCH/DELETE）生效，GET/HEAD/OPTIONS/TRACE 直接放行。
 * 校验逻辑：提取请求的 {@code Origin} 或 {@code Referer} 头中的源（scheme://host:port），
 * 比对请求自身的 {@code Host} 头及配置的允许来源列表，不匹配则拒绝（403）。</p>
 *
 * <p>对纯服务间调用（无浏览器发起、无 Origin/Referer 头）自动放行，不影响内部 RPC。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.csrf")
public class CsrfProperties {

    /**
     * 是否启用 CSRF 防护，默认 {@code true}。
     */
    private boolean enabled = true;

    /**
     * 排除路径（Ant 风格），不参与 CSRF 校验。
     *
     * <p>例如 Webhook 回调、文件上传通知等第三方回调接口。</p>
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * 额外的允许来源（scheme://host:port），补充同源（请求 Host）之外的合法来源。
     *
     * <p>适用于网关/代理在前、通过 X-Forwarded-Host 等头转发请求的场景。
     * 支持完整源（{@code https://example.com}）和端口通配（{@code https://example.com:*}）。</p>
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 严格模式：状态变更请求无 Origin/Referer 头时是否拒绝，默认 {@code false}（放行）。
     *
     * <p>默认 fail-open 以兼容纯服务间调用与老旧客户端；但浏览器配置
     * {@code Referrer-Policy: no-referrer} 时合法同源 POST 也可能不带 Referer，
     * 故默认放行。开启 Cookie 传 token 场景建议设为 {@code true}（fail-closed），
     * 将无来源头的状态变更请求按 CSRF 攻击拒绝，与 SameSite 形成纵深防御.</p>
     */
    private boolean strictMode = false;
}

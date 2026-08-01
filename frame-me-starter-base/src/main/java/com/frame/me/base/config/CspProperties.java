package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Content-Security-Policy 响应头配置属性.
 *
 * <p>CSP 是防御 XSS 和数据注入最有效的浏览器原生机制。默认策略 {@code default-src 'self';
 * frame-ancestors 'none'} 适用于纯 API 服务：仅允许同源加载资源、禁止被嵌入 frame。
 * 前端 SPA / 含第三方 CDN 的业务可按需放宽。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.csp")
public class CspProperties {

    /**
     * 是否添加 CSP 头，默认 {@code true}.
     */
    private boolean enabled = true;

    /**
     * CSP 策略值.
     */
    private String policy = "default-src 'self'; frame-ancestors 'none'";
}

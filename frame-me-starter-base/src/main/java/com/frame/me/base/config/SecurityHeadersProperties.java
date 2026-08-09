package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全响应头配置属性.
 *
 * <p>承载六项浏览器安全响应头：{@code X-Content-Type-Options} / {@code X-Frame-Options}
 * / {@code Referrer-Policy} / {@code Permissions-Policy} / {@code Content-Security-Policy}
 * / {@code Strict-Transport-Security}，统一在 {@code me.security.headers} 下管理.</p>
 *
 * <p>前四项默认开启且取安全默认值；CSP 默认开启（防御 XSS 与数据注入最有效的浏览器机制）；
 * HSTS 默认关闭，由业务在 HTTPS 环境显式开启，避免开发环境自签证书走 HTTPS 时被浏览器长期锁定.
 * 各头值为空字符串表示不设置；CSP/HSTS 另有独立开关.</p>
 *
 * <p>注意 {@code Permissions-Policy}：{@code feature=()} 禁用、{@code feature=*} 全放行，
 * 语义相反；需开放某项时改为 {@code feature=self} 或具体 origin 列表.</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.security.headers")
public class SecurityHeadersProperties {

    /**
     * {@code X-Content-Type-Options} 响应头值，默认 {@code nosniff} 防 MIME 嗅探.
     *
     * <p>空字符串表示不设置该头.</p>
     */
    private String xContentTypeOptions = "nosniff";

    /**
     * {@code X-Frame-Options} 响应头值，默认 {@code DENY} 禁止被嵌入 frame（防 clickjacking）.
     *
     * <p>空字符串表示不设置该头；需同源嵌入时改为 {@code SAMEORIGIN}.</p>
     */
    private String xFrameOptions = "DENY";

    /**
     * {@code Referrer-Policy} 响应头值，默认 {@code strict-origin-when-cross-origin}.
     *
     * <p>空字符串表示不设置该头.</p>
     */
    private String referrerPolicy = "strict-origin-when-cross-origin";

    /**
     * {@code Permissions-Policy} 响应头值，默认禁用一组高敏浏览器 API.
     *
     * <p>纯后端 API 服务用不到浏览器敏感能力，默认禁用地理定位/麦克风/摄像头/支付/
     * USB/陀螺仪/磁力计/加速度计，缩小攻击面。空字符串表示不设置该头.</p>
     */
    private String permissionsPolicy =
            "geolocation=(), microphone=(), camera=(), payment=(), usb=(), "
                    + "gyroscope=(), magnetometer=(), accelerometer=()";

    /**
     * 是否开启 {@code Content-Security-Policy} (CSP)，默认 {@code true}.
     *
     * <p>CSP 是防御 XSS 和数据注入最有效的浏览器原生机制。默认策略适用于纯 API 服务：
     * 仅允许同源加载资源、禁止被嵌入 frame；前端 SPA / 含第三方 CDN 的业务可覆盖 {@link #csp} 放宽.</p>
     */
    private boolean cspEnabled = true;

    /**
     * {@code Content-Security-Policy} 响应头值，默认 {@code default-src 'self'; frame-ancestors 'none'}.
     */
    private String csp = "default-src 'self'; frame-ancestors 'none'";

    /**
     * 是否开启 {@code Strict-Transport-Security} (HSTS)，默认 {@code false}.
     *
     * <p>HSTS 仅在 HTTPS 响应上被浏览器采纳，应用层设置对 HTTP 响应无效；
     * 但为避免开发环境（自签证书走 HTTPS）误开启后被浏览器长期锁定，默认关闭，
     * 由业务在确认 HTTPS 稳定后显式开启。原设计将其完全交由网关/反向代理处理，
     * 现纳入此处统一管理，二者择一即可，避免重复设置.</p>
     */
    private boolean hstsEnabled = false;

    /**
     * {@code Strict-Transport-Security} 响应头值，开启后默认一年 + 含子域.
     *
     * <p>{@code max-age} 单位秒，{@code includeSubDomains} 覆盖子域；
     * {@code preload} 需额外提交到 HSTS Preload 清单且撤回困难，默认不加.</p>
     */
    private String hsts = "max-age=31536000; includeSubDomains";
}

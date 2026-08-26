package com.frame.me.base.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS 跨域配置属性.
 *
 * <p>默认关闭（{@code me.cors.enabled=false}）：CORS 是业务相关能力，由需要跨域的服务显式开启。
 * 开启后注册 {@link org.springframework.web.filter.CorsFilter}，在认证过滤器之前处理 OPTIONS 预检
 * 并附加 CORS 响应头；认证链（AuthFilter/PermissionFilter/PermissionInterceptor）对 OPTIONS
 * 亦各自豁免作兜底。</p>
 *
 * @author frame-me
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "me.cors")
public class CorsProperties {

    /**
     * 是否启用 CORS 处理，默认 false。开启后注册 CorsFilter。
     */
    private boolean enabled = false;

    /**
     * 允许的来源。为空时默认放行所有来源（{@code *}）；显式配置后仅允许列出的来源。
     *
     * <p>底层用 {@code addAllowedOriginPattern} 注册（非 {@code addAllowedOrigin}），
     * pattern 模式下即使 {@code allowCredentials=true} 也可使用 {@code *}，
     * 由框架自动处理「带凭据时不回显 {@code *}、改回显具体来源」的合规逻辑.
     * 若需严格来源白名单，在此显式列出各来源即可.</p>
     */
    private List<String> allowedOrigins = new ArrayList<>();
    /**
     * 允许的 HTTP 方法，默认常用方法。
     */
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
    /**
     * 允许的请求头，默认放行常见头。预检请求 {@code Access-Control-Request-Headers} 中的头需在此列。
     */
    private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "Accept", "X-Requested-With");
    /**
     * 暴露给浏览器可读的响应头，默认仅常规头。自定义响应头需显式列出前端才能读取。
     */
    private List<String> exposedHeaders = List.of("Cache-Control", "Content-Disposition");
    /**
     * 是否允许携带凭据（Cookie），默认 false。设为 true 时 allowedOrigins 不能用 {@code *}。
     */
    private boolean allowCredentials = false;
    /**
     * 预检结果缓存时长（秒），默认 3600。浏览器在此期内对同源预检直接复用结果，不发 OPTIONS。
     */
    private long maxAge = 3600L;

    /**
     * 启动期校验：allowCredentials=true 且 allowedOrigins 为空时打 WARN.
     *
     * <p>pattern 模式下虽不会字面回显 {@code *}，但「回显任意具体 Origin + 携带 Cookie」
     * 等于任意网站可带凭据访问，存在 CSRF 风险。提醒业务方显式配置可信 Origin 白名单.</p>
     */
    @PostConstruct
    void warnOnCredentialsWithWildcardOrigin() {
        if (enabled && allowCredentials && allowedOrigins.isEmpty()) {
            log.warn("[me.cors] allowCredentials=true 且 allowed-origins 为空：当前会向任意来源回显具体 Origin 并允许携带 Cookie，"
                    + "存在 CSRF 风险（任意网站可带凭据访问）。请显式配置 me.cors.allowed-origins 为可信来源白名单");
        }
    }
}

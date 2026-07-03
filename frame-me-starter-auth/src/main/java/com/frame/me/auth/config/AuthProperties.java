package com.frame.me.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证模块配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.auth")
public class AuthProperties {

    /**
     * 是否启用认证模块，默认启用.
     */
    private Boolean enabled = true;

    /**
     * 匿名访问时是否跳过用户解析，默认 {@code false}.
     *
     * <p>为 {@code true} 时，匿名接口不会把请求头中的用户写入上下文。</p>
     */
    private Boolean skipAnonymousContext = false;

    /**
     * 白名单路径列表，支持 Ant 风格通配符.
     *
     * <p>例如：{@code /api/auth/login}、{@code /swagger-ui/**}。</p>
     */
    private List<String> whitelist = new ArrayList<>();
}

package com.frame.me.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关访问日志配置（{@code me.gateway.access-log.*}）.
 *
 * <p>默认关闭；启用后每个请求输出一行 INFO（method / URI 含 query / status / 耗时 / 远端地址），
 * 超长 URI 按 {@link #maxLength} 截断补 {@code ...}，防止撑爆日志行。
 * 与下游 {@code me.auth.access-log.*} 同款约定。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.gateway.access-log")
public class GatewayAccessLogProperties {

    /**
     * 是否启用访问日志，默认 {@code false}（关闭时 Bean 不装配，零开销）.
     */
    private Boolean enabled = false;

    /**
     * URI（含 query string）最大记录长度，超出截断，默认 {@code 2000}.
     *
     * <p>{@code 0} 表示不限制。</p>
     */
    private int maxLength = 2000;
}

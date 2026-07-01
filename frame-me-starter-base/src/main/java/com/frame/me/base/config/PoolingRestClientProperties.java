package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 池化 RestClient 配置属性.
 *
 * <p>绑定前缀 {@code me.restclient.pool}，用于配置 HttpClient 5 连接池参数。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.restclient.pool")
public class PoolingRestClientProperties {

    /**
     * 连接池最大连接总数，默认 200.
     */
    private int maxTotal = 200;

    /**
     * 每个路由的最大连接数，默认 50.
     */
    private int maxPerRoute = 50;
}

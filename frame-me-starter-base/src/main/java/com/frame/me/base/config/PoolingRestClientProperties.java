package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 池化 RestClient 配置属性.
 *
 * <p>绑定前缀 {@code me.restclient.pool}，用于配置 HttpClient 5 连接池参数。
 * 含超时、连接保活、空闲连接驱逐，防下游慢/网络抖动拖垮服务.</p>
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

    /**
     * 连接建立超时，默认 5s.
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 读取/响应超时（等待对端返回数据），默认 30s.
     */
    private Duration responseTimeout = Duration.ofSeconds(30);

    /**
     * 从连接池获取连接的等待超时，默认 5s（防池耗尽时无限阻塞）.
     */
    private Duration connectionRequestTimeout = Duration.ofSeconds(5);

    /**
     * 连接保活时长（Keep-Alive），默认 30s.
     */
    private Duration keepAlive = Duration.ofSeconds(30);

    /**
     * 空闲连接驱逐前的不活跃时间阈值（ms），默认 2000（防复用半关闭连接）.
     */
    private int validateAfterInactivity = 2000;
}


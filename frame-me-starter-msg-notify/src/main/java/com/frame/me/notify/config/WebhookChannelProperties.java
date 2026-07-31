package com.frame.me.notify.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 通道配置属性.
 *
 * <p>通用 HTTP webhook 配置，支持 url、secret、headers
 * 以及该通道下的命名客户端配置。HTTP 超时由 {@code me.restclient.pool.*} 统一配置。</p>
 */
@Data
public class WebhookChannelProperties {

    /**
     * Webhook 请求地址.
     */
    private String url;

    /**
     * 签名密钥（可选）.
     */
    private String secret;

    /**
     * 额外请求头.
     */
    private Map<String, Object> headers = new HashMap<>();

    /**
     * 该通道下的命名客户端配置.
     */
    private Map<String, WebhookChannelProperties> clients = new HashMap<>();
}

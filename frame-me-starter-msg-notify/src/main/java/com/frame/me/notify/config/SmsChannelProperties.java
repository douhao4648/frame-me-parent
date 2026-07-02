package com.frame.me.notify.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信通道配置属性.
 *
 * <p>通用 HTTP 短信网关配置，各厂商可按 url、appKey、appSecret、signName 等字段适配。</p>
 */
@Data
public class SmsChannelProperties {

    /**
     * 短信网关请求地址.
     */
    private String url;

    /**
     * 应用 Key / Access Key.
     */
    private String appKey;

    /**
     * 应用密钥 / Access Secret.
     */
    private String appSecret;

    /**
     * 短信签名.
     */
    private String signName;

    /**
     * 请求超时（毫秒），默认 10000.
     */
    private int timeout = 10000;

    /**
     * 额外请求头.
     */
    private Map<String, Object> headers = new HashMap<>();

    /**
     * 该通道下的命名客户端配置.
     */
    private Map<String, SmsChannelProperties> clients = new HashMap<>();
}

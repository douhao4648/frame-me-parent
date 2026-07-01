package com.frame.me.notify.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮件通道配置属性.
 */
@Data
public class EmailChannelProperties {

    /**
     * SMTP 服务器地址.
     */
    private String host;

    /**
     * SMTP 端口.
     */
    private int port = 25;

    /**
     * 是否需要认证.
     */
    private boolean auth = true;

    /**
     * 是否启用 STARTTLS.
     */
    private boolean startTls = false;

    /**
     * 是否启用 SSL.
     */
    private boolean ssl = false;

    /**
     * 用户名.
     */
    private String username;

    /**
     * 密码.
     */
    private String password;

    /**
     * 发件人地址.
     */
    private String from;

    /**
     * 发件人显示名称.
     */
    private String fromName;

    /**
     * 连接超时（毫秒）.
     */
    private int connectionTimeout = 10000;

    /**
     * 读取超时（毫秒）.
     */
    private int readTimeout = 10000;

    /**
     * 该通道下的命名客户端配置.
     */
    private Map<String, EmailChannelProperties> clients = new HashMap<>();

}

package com.frame.me.op.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计日志配置项.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.audit")
public class AuditProperties {

    /**
     * 是否启用审计模块.
     */
    private boolean enabled = true;

    /**
     * 是否在本地打印审计日志.
     */
    private boolean logEnabled = true;

    /**
     * 审计服务名. 为空时通过事件桥接广播；配置为具体服务名时定向发送.
     */
    private String targetService = "";

    /**
     * 参数 JSON 最大长度，0 表示不限制.
     */
    private int maxParamLength = 0;
}

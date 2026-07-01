package com.frame.me.notify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知模块配置属性.
 *
 * <p>绑定前缀 {@code me.notify}。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.notify")
public class NotifyProperties {

    /** 是否启用通知模块，默认 true. */
    private boolean enabled = true;

    /** 默认邮件通道配置. */
    private EmailChannelProperties email;

    /** 默认 webhook 通道配置. */
    private WebhookChannelProperties webhook;

    /** 默认短信通道配置. */
    private SmsChannelProperties sms;

    /** 全局默认通道类型，如 email / webhook / sms。未配置时无全局默认发送能力。 */
    private String globalDefault;

    /** 全局默认接收者列表。调用方未指定接收者时使用。 */
    private List<String> globalReceivers = new ArrayList<>();

}

package com.frame.me.notify.notify;

import com.frame.me.base.notify.INotifySender;
import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.config.NotifyProperties;
import com.frame.me.notify.util.NotifyClientRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 基于 msg-notify 的 {@link INotifySender} 实现.
 *
 * <p>支持全局默认、指定通道默认、指定命名客户端三种发送方式。
 * 当传入接收者列表为空时，回退到 {@code me.notify.global-receivers}。
 * 当对应通知能力未配置时，方法返回 false 并记录 debug 日志，不抛异常。</p>
 */
@Slf4j
public class MsgNotifySender implements INotifySender {

    private final NotifyProperties notifyProperties;
    private final NotifyClientRegistry notifyClients;

    public MsgNotifySender(NotifyProperties notifyProperties, NotifyClientRegistry notifyClients) {
        this.notifyProperties = notifyProperties;
        this.notifyClients = notifyClients;
    }

    @Override
    public boolean send(String title, String content, List<String> receivers) {
        if (!notifyClients.hasGlobalDefault()) {
            log.debug("Skip notify: global default client is not available");
            return false;
        }
        List<String> targetReceivers = resolveReceivers(receivers);
        if (targetReceivers.isEmpty()) {
            log.debug("Skip notify: no receivers configured");
            return false;
        }
        return notifyClients.send(title, content, targetReceivers).isSuccess();
    }

    @Override
    public boolean sendChannel(String channel, String title, String content, List<String> receivers) {
        if (channel == null || channel.isBlank()) {
            log.debug("Skip channel notify: channel is blank");
            return false;
        }
        INotifyClient client;
        try {
            client = switch (channel) {
                case "email" -> notifyClients.email();
                case "webhook" -> notifyClients.webhook();
                case "sms" -> notifyClients.sms();
                default -> throw new IllegalArgumentException("Unsupported notify channel: " + channel);
            };
        } catch (IllegalStateException | IllegalArgumentException e) {
            // IllegalStateException：该通道未配置默认客户端（如 NotifyClientRegistry.getClient 抛）
            // IllegalArgumentException：channel 不在 email/webhook/sms 支持范围内
            // 两者都按「未配置」语义返回 false，不抛异常（符合类 Javadoc 契约）
            log.debug("Skip channel notify: {}", e.getMessage());
            return false;
        }
        List<String> targetReceivers = resolveReceivers(receivers);
        if (targetReceivers.isEmpty()) {
            log.debug("Skip channel notify: no receivers configured");
            return false;
        }
        return client.send(title, content, targetReceivers).isSuccess();
    }

    @Override
    public boolean sendClient(String clientName, String title, String content, List<String> receivers) {
        INotifyClient client;
        try {
            client = notifyClients.getClient(clientName);
        } catch (IllegalStateException e) {
            log.debug("Skip client notify: {}", e.getMessage());
            return false;
        }
        List<String> targetReceivers = resolveReceivers(receivers);
        if (targetReceivers.isEmpty()) {
            log.debug("Skip client notify: no receivers configured");
            return false;
        }
        return client.send(title, content, targetReceivers).isSuccess();
    }

    /**
     * 解析接收者列表。若调用方未指定，则回退到全局默认接收者。
     *
     * @param receivers 调用方传入的接收者
     * @return 最终使用的接收者列表
     */
    private List<String> resolveReceivers(List<String> receivers) {
        if (receivers != null && !receivers.isEmpty()) {
            return receivers;
        }
        List<String> global = notifyProperties.getGlobalReceivers();
        return global != null ? List.copyOf(global) : List.of();
    }
}

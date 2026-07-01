package com.frame.me.notify.api;

import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;

import java.util.List;

/**
 * 通知客户端统一接口.
 *
 * <p>所有通道实现（邮件、钉钉、飞书等）均需实现此接口，
 * 通过 {@link com.frame.me.notify.util.NotifyClientFactory} 按名称获取实例。</p>
 */
public interface INotifyClient {

    /**
     * 发送通知消息.
     *
     * @param message 通知消息
     * @return 发送结果
     */
    NotifyResult send(NotifyMessage message);

    /**
     * 发送文本通知（多接收者）.
     *
     * @param title     标题
     * @param content   内容
     * @param receivers 接收者列表
     * @return 发送结果
     */
    default NotifyResult send(String title, String content, List<String> receivers) {
        return send(NotifyMessage.of(title, content, receivers));
    }

    /**
     * 发送文本通知（单接收者）.
     *
     * @param title    标题
     * @param content  内容
     * @param receiver 接收者
     * @return 发送结果
     */
    default NotifyResult send(String title, String content, String receiver) {
        return send(NotifyMessage.of(title, content, List.of(receiver)));
    }

    /**
     * 获取客户端名称.
     *
     * @return 名称标识
     */
    String getName();

    /**
     * 获取通道类型.
     *
     * @return 通道类型
     */
    String getChannelType();

    /**
     * 检查客户端是否可用.
     *
     * @return true 表示可用
     */
    default boolean isAvailable() {
        return true;
    }
}

package com.frame.me.base.notify;

import java.util.List;

/**
 * 通用通知发送接口.
 *
 * <p>由具体通知 starter（如 frame-me-starter-msg-notify）提供实现，
 * 业务代码通过该接口发送通知而无需关心底层通道。</p>
 */
public interface INotifySender {

    /**
     * 使用全局默认配置发送通知.
     *
     * @param title     标题
     * @param content   内容
     * @param receivers 接收者列表
     * @return 是否发送成功
     */
    boolean send(String title, String content, List<String> receivers);

    /**
     * 使用全局默认配置发送通知（单个接收者便捷方法）。
     *
     * @param title     标题
     * @param content   内容
     * @param receiver  接收者
     * @return 是否发送成功
     */
    default boolean send(String title, String content, String receiver) {
        return send(title, content, receiver == null || receiver.isBlank() ? List.of() : List.of(receiver));
    }

    /**
     * 使用指定通道的默认客户端发送通知（单个接收者便捷方法）。
     *
     * @param channel   通道类型，如 email / webhook / sms
     * @param title     标题
     * @param content   内容
     * @param receiver  接收者
     * @return 是否发送成功
     */
    default boolean sendChannel(String channel, String title, String content, String receiver) {
        return sendChannel(channel, title, content, receiver == null || receiver.isBlank() ? List.of() : List.of(receiver));
    }

    /**
     * 使用指定命名客户端发送通知（单个接收者便捷方法）。
     *
     * @param clientName 完整客户端名称，如 email:alert / webhook:ops / sms:marketing
     * @param title      标题
     * @param content    内容
     * @param receiver   接收者
     * @return 是否发送成功
     */
    default boolean sendClient(String clientName, String title, String content, String receiver) {
        return sendClient(clientName, title, content, receiver == null || receiver.isBlank() ? List.of() : List.of(receiver));
    }

    /**
     * 使用指定通道的默认客户端发送通知.
     *
     * @param channel   通道类型，如 email / webhook / sms
     * @param title     标题
     * @param content   内容
     * @param receivers 接收者列表
     * @return 是否发送成功
     */
    boolean sendChannel(String channel, String title, String content, List<String> receivers);

    /**
     * 使用指定命名客户端发送通知.
     *
     * @param clientName 完整客户端名称，如 email:alert / webhook:ops / sms:marketing
     * @param title      标题
     * @param content    内容
     * @param receivers  接收者列表
     * @return 是否发送成功
     */
    boolean sendClient(String clientName, String title, String content, List<String> receivers);
}

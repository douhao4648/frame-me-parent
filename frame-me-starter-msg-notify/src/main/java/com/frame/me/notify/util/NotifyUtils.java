package com.frame.me.notify.util;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;

import java.util.List;
import java.util.Optional;

/**
 * 通知工具类统一入口.
 *
 * <p>按通道类型提供默认或指定客户端实例，同时支持通过全局默认客户端发送消息：
 * <ul>
 *     <li>{@link #send(NotifyMessage)} 使用全局默认客户端发送消息</li>
 *     <li>{@link #defaultClient()} 获取全局默认客户端</li>
 *     <li>{@link #email()} 默认邮件客户端</li>
 *     <li>{@link #email(String)} 指定邮件客户端</li>
 *     <li>{@link #webhook()} 默认 webhook 客户端</li>
 *     <li>{@link #webhook(String)} 指定 webhook 客户端</li>
 *     <li>{@link #sms()} 默认短信客户端</li>
 *     <li>{@link #sms(String)} 指定短信客户端</li>
 * </ul>
 * </p>
 */
public final class NotifyUtils {

    private static final String EMAIL = "email";
    private static final String WEBHOOK = "webhook";
    private static final String SMS = "sms";

    private static final String NO_GLOBAL_DEFAULT_CODE = "NO_GLOBAL_DEFAULT";
    private static final String NO_GLOBAL_DEFAULT_MESSAGE = "Global default notify client is not configured or not available";

    private NotifyUtils() {
    }

    /**
     * 使用全局默认客户端发送消息.
     *
     * <p>若未配置全局默认客户端或对应客户端不可用，返回失败结果，不实际发送。</p>
     *
     * @param message 通知消息
     * @return 发送结果
     */
    public static NotifyResult send(NotifyMessage message) {
        return getGlobalDefaultClient()
                .map(client -> client.send(message))
                .orElseGet(() -> NotifyResult.fail(NO_GLOBAL_DEFAULT_CODE, NO_GLOBAL_DEFAULT_MESSAGE));
    }

    /**
     * 使用全局默认客户端发送文本通知（多接收者）.
     *
     * @param title     标题
     * @param content   内容
     * @param receivers 接收者列表
     * @return 发送结果
     */
    public static NotifyResult send(String title, String content, List<String> receivers) {
        return send(NotifyMessage.of(title, content, receivers));
    }

    /**
     * 使用全局默认客户端发送文本通知（单接收者）.
     *
     * @param title    标题
     * @param content  内容
     * @param receiver 接收者
     * @return 发送结果
     */
    public static NotifyResult send(String title, String content, String receiver) {
        return send(NotifyMessage.of(title, content, List.of(receiver)));
    }

    /**
     * 获取全局默认客户端.
     *
     * @return 全局默认客户端，未配置时返回 {@link Optional#empty()}
     */
    public static Optional<INotifyClient> defaultClient() {
        return getGlobalDefaultClient();
    }

    /**
     * 获取默认邮件客户端.
     *
     * @return 默认邮件客户端
     */
    public static INotifyClient email() {
        return getDefaultClient(EMAIL);
    }

    /**
     * 获取指定名称的邮件客户端.
     *
     * @param name 客户端名称
     * @return 对应邮件客户端
     */
    public static INotifyClient email(String name) {
        return getNamedClient(EMAIL, name);
    }

    /**
     * 获取默认 webhook 客户端.
     *
     * @return 默认 webhook 客户端
     */
    public static INotifyClient webhook() {
        return getDefaultClient(WEBHOOK);
    }

    /**
     * 获取指定名称的 webhook 客户端.
     *
     * @param name 客户端名称
     * @return 对应 webhook 客户端
     */
    public static INotifyClient webhook(String name) {
        return getNamedClient(WEBHOOK, name);
    }

    /**
     * 获取默认短信客户端.
     *
     * @return 默认短信客户端
     */
    public static INotifyClient sms() {
        return getDefaultClient(SMS);
    }

    /**
     * 获取指定名称的短信客户端.
     *
     * @param name 客户端名称
     * @return 对应短信客户端
     */
    public static INotifyClient sms(String name) {
        return getNamedClient(SMS, name);
    }

    private static Optional<INotifyClient> getGlobalDefaultClient() {
        return NotifyClientFactory.getGlobalDefaultClient();
    }

    private static INotifyClient getDefaultClient(String channelType) {
        String defaultName = NotifyClientFactory.getDefaultClientName(channelType);
        INotifyClient client = NotifyClientFactory.getClient(defaultName);
        if (!channelType.equals(client.getChannelType())) {
            throw new IllegalStateException(
                    "Default client '" + defaultName + "' is not a " + channelType + " client, type=" + client.getChannelType());
        }
        return client;
    }

    private static INotifyClient getNamedClient(String channelType, String name) {
        String fullName = name.contains(":") ? name : channelType + ":" + name;
        INotifyClient client = NotifyClientFactory.getClient(fullName);
        if (!channelType.equals(client.getChannelType())) {
            throw new IllegalStateException(
                    "Client '" + name + "' is not a " + channelType + " client, type=" + client.getChannelType());
        }
        return client;
    }
}

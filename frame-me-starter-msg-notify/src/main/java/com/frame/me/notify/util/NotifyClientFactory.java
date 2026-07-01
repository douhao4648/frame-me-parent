package com.frame.me.notify.util;

import com.frame.me.notify.api.INotifyClient;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知客户端工厂.
 *
 * <p>管理所有已注册的 INotifyClient 实例，按名称或通道类型默认名称获取，
 * 同时支持全局默认客户端。</p>
 */
public final class NotifyClientFactory {

    private static final Map<String, INotifyClient> CLIENT_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> CHANNEL_DEFAULT_NAMES = new ConcurrentHashMap<>();
    private static String globalDefaultName;

    private NotifyClientFactory() {
    }

    /**
     * 初始化客户端映射，并指定各通道类型的默认客户端名称.
     *
     * <p>由 {@link com.frame.me.notify.config.NotifyAutoConfiguration} 调用。</p>
     *
     * @param clients         所有客户端实例
     * @param channelDefaults 通道类型到默认客户端名称的映射
     */
    public static void init(Map<String, INotifyClient> clients,
                            Map<String, String> channelDefaults) {
        init(clients, channelDefaults, null);
    }

    /**
     * 初始化客户端映射，指定各通道类型的默认客户端名称，并设置全局默认客户端.
     *
     * <p>由 {@link com.frame.me.notify.config.NotifyAutoConfiguration} 调用。</p>
     *
     * @param clients         所有客户端实例
     * @param channelDefaults 通道类型到默认客户端名称的映射
     * @param globalDefault   全局默认客户端名称，为 null 表示不设置
     */
    public static void init(Map<String, INotifyClient> clients,
                            Map<String, String> channelDefaults,
                            String globalDefault) {
        CHANNEL_DEFAULT_NAMES.clear();
        if (channelDefaults != null) {
            CHANNEL_DEFAULT_NAMES.putAll(channelDefaults);
        }
        CLIENT_MAP.clear();
        CLIENT_MAP.putAll(clients);
        globalDefaultName = globalDefault;
    }

    /**
     * 获取指定通道类型的默认客户端名称.
     *
     * @param channelType 通道类型，如 email / webhook
     * @return 对应默认客户端名称
     * @throws IllegalStateException 该通道未配置默认客户端时
     */
    public static String getDefaultClientName(String channelType) {
        String name = CHANNEL_DEFAULT_NAMES.get(channelType);
        if (name == null) {
            throw new IllegalStateException(
                    "No default client configured for channel '" + channelType + "'. Please check me.notify." + channelType + " configuration");
        }
        return name;
    }

    /**
     * 获取指定名称的客户端.
     *
     * @param name 客户端名称
     * @return 对应客户端
     * @throws IllegalStateException 客户端不存在时
     */
    public static INotifyClient getClient(String name) {
        INotifyClient client = CLIENT_MAP.get(name);
        if (client == null) {
            throw new IllegalStateException("Notify client '" + name + "' not registered. Please check the me.notify configuration");
        }
        return client;
    }

    /**
     * 获取全局默认客户端.
     *
     * @return 全局默认客户端，未配置或不存在时返回 {@link Optional#empty()}
     */
    public static Optional<INotifyClient> getGlobalDefaultClient() {
        if (globalDefaultName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CLIENT_MAP.get(globalDefaultName));
    }

    /**
     * 判断是否包含指定客户端.
     *
     * @param name 客户端名称
     * @return true 表示已注册
     */
    public static boolean hasClient(String name) {
        return CLIENT_MAP.containsKey(name);
    }

    /**
     * 判断是否配置了有效的全局默认客户端.
     *
     * @return true 表示已配置且对应客户端存在
     */
    public static boolean hasGlobalDefault() {
        return globalDefaultName != null && CLIENT_MAP.containsKey(globalDefaultName);
    }

    /**
     * 获取所有已注册客户端名称.
     *
     * @return 客户端名称集合
     */
    public static Set<String> clientNames() {
        return CLIENT_MAP.keySet();
    }
}

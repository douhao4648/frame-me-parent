package com.frame.me.auth.jwt.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Refresh Token 存储兜底实现.
 *
 * <p>仅在未引入 {@code frame-me-starter-multi-redis} 且业务未自定义
 * {@link IRefreshTokenStore} 时装配，单实例可用；多实例部署下 Refresh Token
 * 不跨实例共享（刷新、强制登出仅当前实例生效），生产环境应引入 multi-redis
 * 切换为 {@link RedisRefreshTokenStore}。</p>
 *
 * <p>ponytail: 惰性过期（读取时判断并清除），无后台清理线程；过期条目在下次
 * 访问前驻留内存，Refresh Token 量级下可忽略，量大再加定时清理。</p>
 *
 * @author frame-me
 */
public class InMemoryRefreshTokenStore implements IRefreshTokenStore {

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void save(Long userId, String refreshToken, Duration expires) {
        store.put(userId, new Entry(refreshToken, System.currentTimeMillis() + expires.toMillis()));
    }

    @Override
    public String get(Long userId) {
        Entry entry = store.get(userId);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAtMillis()) {
            store.remove(userId, entry);
            return null;
        }
        return entry.token();
    }

    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }

    /**
     * 存储条目：token + 过期时间戳（毫秒）.
     */
    private record Entry(String token, long expireAtMillis) {
    }
}

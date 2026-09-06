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

    /** 最大存储条目数，防止 DDoS 登录攻击撑爆内存. */
    private static final int MAX_SIZE = 10_000;

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();
    private final Object evictLock = new Object();

    /**
     * 上游 token 与 refresh token 同用户量级（仅 RP 登录会写入），复用惰性过期，
     * 不单独做容量闸门——{@code store} 的 MAX_SIZE 已间接约束.
     *
     * <p>结构：userId → (appId → token)，按应用隔离；TTL 挂在用户级（与 Redis hash
     * 实现的共享 TTL 语义对齐）。</p>
     */
    private final Map<Long, UpstreamEntry> upstreamStore = new ConcurrentHashMap<>();

    @Override
    public void save(Long userId, String refreshToken, Duration expires) {
        // ponytail: 扫一遍 ConcurrentHashMap 找最早过期项 + 顺路清除已过期条目，O(N) 但 N≤10k 可接受；
        // synchronized 防止并发 save 下多线程同时淘汰导致 size 短暂超过 MAX_SIZE
        if (store.size() >= MAX_SIZE) {
            synchronized (evictLock) {
                if (store.size() >= MAX_SIZE) {
                    long now = System.currentTimeMillis();
                    Long oldest = null;
                    long minExpire = Long.MAX_VALUE;
                    for (Map.Entry<Long, Entry> e : store.entrySet()) {
                        long exp = e.getValue().expireAtMillis();
                        if (exp <= now) {
                            store.remove(e.getKey());
                        } else if (exp < minExpire) {
                            minExpire = exp;
                            oldest = e.getKey();
                        }
                    }
                    if (oldest != null) {
                        store.remove(oldest);
                    }
                }
            }
        }
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

    @Override
    public void saveUpstreamToken(Long userId, String appId, String upstreamToken, Duration expires) {
        upstreamStore.compute(userId, (id, entry) -> {
            Map<String, String> tokens = entry == null ? new ConcurrentHashMap<>() : entry.tokens();
            tokens.put(appId, upstreamToken);
            return new UpstreamEntry(tokens, System.currentTimeMillis() + expires.toMillis());
        });
    }

    @Override
    public String getUpstreamToken(Long userId, String appId) {
        UpstreamEntry entry = upstreamStore.get(userId);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAtMillis()) {
            upstreamStore.remove(userId, entry);
            return null;
        }
        return entry.tokens().get(appId);
    }

    @Override
    public void deleteUpstreamTokens(Long userId) {
        upstreamStore.remove(userId);
    }

    @Override
    public void renewUpstreamTokens(Long userId, Duration expires) {
        upstreamStore.computeIfPresent(userId, (id, entry) ->
                new UpstreamEntry(entry.tokens(), System.currentTimeMillis() + expires.toMillis()));
    }

    /**
     * 上游 token 条目：appId → token 的映射 + 用户级过期时间戳（毫秒）.
     *
     * <p>不用 record：P3C 会把 record 头误判为方法名（UpstreamEntry 不符合
     * lowerCamelCase），普通 final class 构造器不会被扫描.</p>
     */
    private static final class UpstreamEntry {

        private final Map<String, String> tokens;
        private final long expireAtMillis;

        private UpstreamEntry(Map<String, String> tokens, long expireAtMillis) {
            this.tokens = tokens;
            this.expireAtMillis = expireAtMillis;
        }

        private Map<String, String> tokens() {
            return tokens;
        }

        private long expireAtMillis() {
            return expireAtMillis;
        }
    }

    /**
     * 存储条目：token + 过期时间戳（毫秒）.
     *
     * <p>不用 record：同 {@link UpstreamEntry} 的 P3C 误判说明.</p>
     */
    private static final class Entry {

        private final String token;
        private final long expireAtMillis;

        private Entry(String token, long expireAtMillis) {
            this.token = token;
            this.expireAtMillis = expireAtMillis;
        }

        private String token() {
            return token;
        }

        private long expireAtMillis() {
            return expireAtMillis;
        }
    }
}

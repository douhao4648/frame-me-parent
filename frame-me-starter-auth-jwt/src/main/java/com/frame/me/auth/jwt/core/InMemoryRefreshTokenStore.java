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

    /** 上游 token 存储独立容量上限（与 Redis 实现的独立 TTL 语义对齐，不与 store 生命周期强绑定）. */
    private static final int MAX_UPSTREAM_SIZE = 10_000;

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();
    private final Object evictLock = new Object();

    /**
     * 上游 token 与 refresh token 同用户量级（仅 RP 登录会写入），复用惰性过期；
     * 容量独立于 {@code store} 受 {@link #MAX_UPSTREAM_SIZE} 约束，淘汰语义同 {@link #save}.
     *
     * <p>结构：userId → (appId → token)，按应用隔离；TTL 挂在用户级（与 Redis hash
     * 实现的共享 TTL 语义对齐）。</p>
     */
    private final Map<Long, UpstreamEntry> upstreamStore = new ConcurrentHashMap<>();

    @Override
    public void save(Long userId, String refreshToken, Duration expires) {
        // ponytail: 扫一遍 ConcurrentHashMap 找最早过期项 + 顺路清除已过期条目，O(N) 但 N≤10k 可接受；
        // 容量检查、淘汰、插入放同一临界区：覆盖已有用户不淘汰（size 不变）；
        // 并发新增不会越过 MAX_SIZE，也不会误淘汰其他用户的有效 token
        Entry newEntry = new Entry(refreshToken, System.currentTimeMillis() + expires.toMillis());
        synchronized (evictLock) {
            if (!store.containsKey(userId) && store.size() >= MAX_SIZE) {
                evictExpiredThenOldest(store, MAX_SIZE);
            }
            store.put(userId, newEntry);
        }
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
    public boolean rotate(Long userId, String expectedToken, String newToken, Duration expires) {
        Entry current = store.get(userId);
        if (current == null || System.currentTimeMillis() > current.expireAtMillis()
                || !current.token().equals(expectedToken)) {
            return false;
        }
        Entry replacement = new Entry(newToken, System.currentTimeMillis() + expires.toMillis());
        return store.replace(userId, current, replacement);
    }

    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }

    @Override
    public void saveUpstreamToken(Long userId, String appId, String upstreamToken, Duration expires) {
        long now = System.currentTimeMillis();
        synchronized (evictLock) {
            if (!upstreamStore.containsKey(userId) && upstreamStore.size() >= MAX_UPSTREAM_SIZE) {
                evictExpiredThenOldest(upstreamStore, MAX_UPSTREAM_SIZE);
            }
            upstreamStore.compute(userId, (id, entry) -> {
                // 过期 entry 视为不存在（对齐 Redis hash 过期后字段全失的语义）：
                // 不复用旧应用映射，避免写入另一应用时复活已过期的旧 token；
                // 过期判定与读取路径一致（now > expireAt 才算过期）
                Map<String, String> tokens = entry == null || now > entry.expireAtMillis()
                        ? new ConcurrentHashMap<>() : entry.tokens();
                tokens.put(appId, upstreamToken);
                return new UpstreamEntry(tokens, now + expires.toMillis());
            });
        }
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
        // 过期 entry 视为不存在：返回 null 直接移除，不给过期条目（连同旧 token）续命
        upstreamStore.computeIfPresent(userId, (id, entry) ->
                System.currentTimeMillis() > entry.expireAtMillis() ? null
                        : new UpstreamEntry(entry.tokens(), System.currentTimeMillis() + expires.toMillis()));
    }

    /**
     * 清掉已过期条目；仍满则淘汰最早过期的一项（调用方须持有 {@link #evictLock}）.
     *
     * <p>过期判定与读取路径一致：{@code now > expireAt} 才算过期。</p>
     */
    private static void evictExpiredThenOldest(Map<Long, ? extends Expirable> map, int maxSize) {
        long now = System.currentTimeMillis();
        Long oldest = null;
        long minExpire = Long.MAX_VALUE;
        for (Map.Entry<Long, ? extends Expirable> e : map.entrySet()) {
            long exp = e.getValue().expireAtMillis();
            if (now > exp) {
                map.remove(e.getKey());
            } else if (exp < minExpire) {
                minExpire = exp;
                oldest = e.getKey();
            }
        }
        if (map.size() >= maxSize && oldest != null) {
            map.remove(oldest);
        }
    }

    /**
     * 带过期时间戳的条目（供淘汰逻辑统一处理）.
     */
    private interface Expirable {

        long expireAtMillis();
    }

    /**
     * 上游 token 条目：appId → token 的映射 + 用户级过期时间戳（毫秒）.
     *
     * <p>不用 record：P3C 会把 record 头误判为方法名（UpstreamEntry 不符合
     * lowerCamelCase），普通 final class 构造器不会被扫描.</p>
     */
    private static final class UpstreamEntry implements Expirable {

        private final Map<String, String> tokens;
        private final long expireAtMillis;

        private UpstreamEntry(Map<String, String> tokens, long expireAtMillis) {
            this.tokens = tokens;
            this.expireAtMillis = expireAtMillis;
        }

        private Map<String, String> tokens() {
            return tokens;
        }

        @Override
        public long expireAtMillis() {
            return expireAtMillis;
        }
    }

    /**
     * 存储条目：token + 过期时间戳（毫秒）.
     *
     * <p>不用 record：同 {@link UpstreamEntry} 的 P3C 误判说明.</p>
     */
    private static final class Entry implements Expirable {

        private final String token;
        private final long expireAtMillis;

        private Entry(String token, long expireAtMillis) {
            this.token = token;
            this.expireAtMillis = expireAtMillis;
        }

        private String token() {
            return token;
        }

        @Override
        public long expireAtMillis() {
            return expireAtMillis;
        }
    }
}

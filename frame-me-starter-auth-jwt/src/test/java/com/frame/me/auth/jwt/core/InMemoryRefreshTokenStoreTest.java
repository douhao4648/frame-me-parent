package com.frame.me.auth.jwt.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryRefreshTokenStore} 单元测试.
 *
 * @author frame-me
 */
class InMemoryRefreshTokenStoreTest {

    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Test
    void saveAndGet() {
        store.save(1L, "token-1", Duration.ofMinutes(30));
        assertEquals("token-1", store.get(1L));
    }

    @Test
    void getReturnsNullWhenExpired() {
        store.save(1L, "token-1", Duration.ofSeconds(-1));
        assertNull(store.get(1L));
        // 过期条目被惰性清除
        store.save(1L, "token-2", Duration.ofMinutes(30));
        assertEquals("token-2", store.get(1L));
    }

    @Test
    void delete() {
        store.save(1L, "token-1", Duration.ofMinutes(30));
        store.delete(1L);
        assertNull(store.get(1L));
    }

    @Test
    void getMissingReturnsNull() {
        assertNull(store.get(99L));
    }

    @Test
    void rotateReplacesOnlyMatchingCurrentToken() {
        store.save(1L, "old-token", Duration.ofMinutes(30));

        assertTrue(store.rotate(1L, "old-token", "new-token", Duration.ofMinutes(30)));
        assertEquals("new-token", store.get(1L));
        assertFalse(store.rotate(1L, "old-token", "attacker-token", Duration.ofMinutes(30)));
        assertEquals("new-token", store.get(1L));
    }

    /**
     * 容量满后更新已有用户的 token 是覆盖（size 不变），不得淘汰其他用户的有效 token.
     */
    @Test
    void saveExistingUserAtCapacityDoesNotEvictOthers() {
        for (long i = 0; i < 10_000; i++) {
            store.save(i, "token-" + i, Duration.ofMinutes(30));
        }

        store.save(9_999L, "token-9999-v2", Duration.ofMinutes(30));

        assertEquals("token-9999-v2", store.get(9_999L));
        assertEquals("token-0", store.get(0L), "覆盖已有用户不得挤掉其他用户的有效 token");
    }

    /**
     * 容量满后新增用户才淘汰：优先清过期条目，仍满则淘汰最早过期的一项.
     */
    @Test
    void saveNewUserAtCapacityEvictsOnlyOne() {
        for (long i = 0; i < 10_000; i++) {
            store.save(i, "token-" + i, Duration.ofMinutes(30));
        }
        // 用户 0 已过期：新增时应优先清掉它，其他有效 token 全部保留
        store.save(0L, "expired", Duration.ofSeconds(-1));

        store.save(10_000L, "token-new", Duration.ofMinutes(30));

        assertEquals("token-new", store.get(10_000L));
        assertNull(store.get(0L), "过期条目应被优先清理");
        assertEquals("token-1", store.get(1L), "清理过期后已腾出位置，不得再淘汰有效 token");
    }

    /**
     * 上游 token 存储有独立容量上限：满后新增用户先清过期、再淘汰最早过期一项；
     * 覆盖已有用户不淘汰.
     */
    @Test
    void upstreamStoreHasIndependentCapacityCap() {
        for (long i = 0; i < 10_000; i++) {
            store.saveUpstreamToken(i, "app", "up-" + i, Duration.ofMinutes(30));
        }

        // 覆盖已有用户：不淘汰任何人
        store.saveUpstreamToken(9_999L, "app", "up-9999-v2", Duration.ofMinutes(30));
        assertEquals("up-9999-v2", store.getUpstreamToken(9_999L, "app"));
        assertEquals("up-0", store.getUpstreamToken(0L, "app"), "覆盖已有用户不得淘汰其他用户的上游 token");

        // 把用户 0 改为更短 TTL，使其确定性地成为"最早过期"（同毫秒建桶无法保证淘汰顺序）
        store.saveUpstreamToken(0L, "app", "up-0", Duration.ofMinutes(1));

        // 新增用户：淘汰最早过期的一项（用户 0），容量不超上限
        store.saveUpstreamToken(10_000L, "app", "up-new", Duration.ofMinutes(30));
        assertEquals("up-new", store.getUpstreamToken(10_000L, "app"));
        assertNull(store.getUpstreamToken(0L, "app"), "新增满容量时应淘汰最早过期的一项");
        assertEquals("up-1", store.getUpstreamToken(1L, "app"), "一次只淘汰一项，不得误伤其他用户");
    }

    /**
     * 过期 entry 视为不存在（对齐 Redis hash 过期语义）：写入另一应用不得复活已过期的旧 token.
     */
    @Test
    void saveUpstreamTokenDoesNotResurrectExpiredEntry() {
        store.saveUpstreamToken(1L, "appA", "token-a", Duration.ofSeconds(-1));

        store.saveUpstreamToken(1L, "appB", "token-b", Duration.ofMinutes(30));

        assertEquals("token-b", store.getUpstreamToken(1L, "appB"));
        assertNull(store.getUpstreamToken(1L, "appA"), "已过期的旧 token 不得随新应用写入而复活");
    }

    /**
     * 过期 entry 不得续期：renew 后条目应被移除，而不是连旧 token 一起复活.
     */
    @Test
    void renewUpstreamTokensDropsExpiredEntry() {
        store.saveUpstreamToken(2L, "appA", "token-a", Duration.ofSeconds(-1));

        store.renewUpstreamTokens(2L, Duration.ofMinutes(30));

        assertNull(store.getUpstreamToken(2L, "appA"), "过期 entry 续期应视为不存在并移除");
    }
}

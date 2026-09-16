package com.frame.me.redis.util;

import org.junit.jupiter.api.Test;
import org.redisson.RedissonMultiLock;
import org.redisson.RedissonRedLock;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RedissonSync 单元测试.
 */
class RedissonSyncTest {

    @Test
    void shouldReturnLockObjects() {
        RedissonClient client = mock(RedissonClient.class);
        RReadWriteLock readWriteLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        RLock writeLock = mock(RLock.class);
        RLock fairLock = mock(RLock.class);
        RSemaphore semaphore = mock(RSemaphore.class);
        RCountDownLatch latch = mock(RCountDownLatch.class);
        RPermitExpirableSemaphore permitSemaphore = mock(RPermitExpirableSemaphore.class);
        RLock lockA = mock(RLock.class);
        RLock lockB = mock(RLock.class);

        when(client.getReadWriteLock("rw")).thenReturn(readWriteLock);
        when(readWriteLock.readLock()).thenReturn(readLock);
        when(readWriteLock.writeLock()).thenReturn(writeLock);
        when(client.getFairLock("fair")).thenReturn(fairLock);
        when(client.getSemaphore("sem")).thenReturn(semaphore);
        when(client.getCountDownLatch("latch")).thenReturn(latch);
        when(client.getPermitExpirableSemaphore("permit")).thenReturn(permitSemaphore);
        when(client.getLock("a")).thenReturn(lockA);
        when(client.getLock("b")).thenReturn(lockB);

        RedissonSync redissonSync = new RedissonSync(client);

        assertNotNull(redissonSync.getReadWriteLock("rw"));
        assertNotNull(redissonSync.readLock("rw"));
        assertNotNull(redissonSync.writeLock("rw"));
        assertNotNull(redissonSync.getFairLock("fair"));
        assertNotNull(redissonSync.getSemaphore("sem"));
        assertNotNull(redissonSync.getCountDownLatch("latch"));
        assertNotNull(redissonSync.getPermitExpirableSemaphore("permit"));
        RedissonRedLock redLock = redissonSync.getRedLock("a", "b");
        RedissonMultiLock multiLock = redissonSync.getMultiLock("a", "b");
        assertNotNull(redLock);
        assertNotNull(multiLock);
    }
}

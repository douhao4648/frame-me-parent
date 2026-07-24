package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.dao.SaTokenDao;
import com.frame.me.auth.satoken.config.SaTokenAuthProperties;
import com.frame.me.redis.util.RedisClient;
import com.frame.me.redis.util.RedisUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisSaTokenDao} 单元测试.
 *
 * <p>不引入真实 Redis：静态打桩 {@link RedisUtils#getClient(String)} 返回 mock 客户端，
 * 逐方法验证 timeout 分支语义（对齐官方 {@code SaTokenDaoForRedisTemplate}）与
 * key 原样透传（sa-token 生成的 key 自带 tokenName 前缀，本 DAO 不再叠加命名空间）。</p>
 *
 * @author frame-me
 */
class RedisSaTokenDaoTest {

    /**
     * sa-token 自身生成的 key 样例（自带 tokenName 前缀）.
     */
    private static final String KEY = "satoken:login:token:k1";

    private final SaTokenAuthProperties properties = new SaTokenAuthProperties();
    private final RedisSaTokenDao dao = new RedisSaTokenDao(properties.getRedis().getClientName());
    private final RedisClient client = mock(RedisClient.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<Object, Object> redisTemplate = mock(RedisTemplate.class);

    private MockedStatic<RedisUtils> redisUtilsMock;

    @BeforeEach
    void setUp() {
        redisUtilsMock = mockStatic(RedisUtils.class);
        redisUtilsMock.when(() -> RedisUtils.getClient("default")).thenReturn(client);
    }

    @AfterEach
    void tearDown() {
        redisUtilsMock.close();
    }

    /**
     * 读取：key 原样透传委托客户端.
     */
    @Test
    void get_passesKeyThrough() {
        when(client.get(KEY)).thenReturn("v1");
        assertThat(dao.get(KEY)).isEqualTo("v1");
    }

    /**
     * 写入：timeout &gt; 0 → 限时秒级存储.
     */
    @Test
    void set_positiveTimeout_setsWithSeconds() {
        dao.set(KEY, "v1", 100L);
        verify(client).set(KEY, "v1", 100L, TimeUnit.SECONDS);
    }

    /**
     * 写入：timeout = -1（NEVER_EXPIRE）→ 无过期存储.
     */
    @Test
    void set_neverExpire_setsWithoutExpire() {
        dao.set(KEY, "v1", SaTokenDao.NEVER_EXPIRE);
        verify(client).set(KEY, "v1");
    }

    /**
     * 写入：timeout = 0 或 &lt;= -2（NOT_VALUE_EXPIRE）→ 不存储直接返回.
     */
    @Test
    void set_zeroOrNotValueExpire_skips() {
        dao.set(KEY, "v1", 0L);
        dao.set(KEY, "v1", SaTokenDao.NOT_VALUE_EXPIRE);
        dao.set(KEY, "v1", -100L);
        verify(client, never()).set(anyString(), anyString());
        verify(client, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    /**
     * 更新：key 不存在（-2）→ 不写入.
     */
    @Test
    void update_keyMissing_skips() {
        when(client.obj()).thenReturn(redisTemplate);
        when(redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(SaTokenDao.NOT_VALUE_EXPIRE);

        dao.update(KEY, "v1");

        verify(client, never()).set(anyString(), anyString());
        verify(client, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    /**
     * 更新：key 永不过期（-1）→ 无过期重写.
     */
    @Test
    void update_neverExpire_rewritesWithoutExpire() {
        when(client.obj()).thenReturn(redisTemplate);
        when(redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(SaTokenDao.NEVER_EXPIRE);

        dao.update(KEY, "v1");

        verify(client).set(KEY, "v1");
    }

    /**
     * 更新：限时 key → 以剩余毫秒数重写（过期时间不变语义）.
     */
    @Test
    void update_limitedKey_rewritesWithRemainingMs() {
        when(client.obj()).thenReturn(redisTemplate);
        when(redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(5000L);

        dao.update(KEY, "v1");

        verify(client).set(KEY, "v1", 5000L, TimeUnit.MILLISECONDS);
    }

    /**
     * 删除：key 原样透传委托客户端.
     */
    @Test
    void delete_passesKeyThrough() {
        dao.delete(KEY);
        verify(client).delete(KEY);
    }

    /**
     * 剩余存活时间：直接透传客户端秒数（-1 永久 / -2 不存在与 sa-token 常量一致）.
     */
    @Test
    void getTimeout_delegatesSeconds() {
        when(client.getExpire(KEY)).thenReturn(500L);
        assertThat(dao.getTimeout(KEY)).isEqualTo(500L);

        when(client.getExpire("satoken:login:token:k2")).thenReturn(-1L);
        assertThat(dao.getTimeout("satoken:login:token:k2")).isEqualTo(SaTokenDao.NEVER_EXPIRE);
    }

    /**
     * 修改存活时间：目标永久 → PERSIST 一次往返移除 TTL（已永久或 key 不存在时 Redis 自然跳过）.
     */
    @Test
    void updateTimeout_neverExpire_persistsKey() {
        when(client.obj()).thenReturn(redisTemplate);

        dao.updateTimeout(KEY, SaTokenDao.NEVER_EXPIRE);

        verify(redisTemplate).persist(KEY);
        verify(client, never()).set(anyString(), anyString());
        verify(client, never()).expire(anyString(), any(Duration.class));
    }

    /**
     * 修改存活时间：限时 → EXPIRE 秒级.
     */
    @Test
    void updateTimeout_limited_callsExpire() {
        dao.updateTimeout(KEY, 100L);
        verify(client).expire(KEY, Duration.ofSeconds(100L));
    }

    /**
     * 搜索：经原生连接 KEYS，pattern 组成为 prefix + "*" + keyword + "*"（prefix 即
     * sa-token 传入的原样前缀），结果按 sa-token 语义分页.
     */
    @Test
    void searchData_viaRawConnectionKeys() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        when(client.obj()).thenReturn(redisTemplate);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.keyCommands()).thenReturn(keyCommands);

        Set<byte[]> rawKeys = new LinkedHashSet<>();
        rawKeys.add("satoken:login:token:aaa".getBytes(StandardCharsets.UTF_8));
        rawKeys.add("satoken:login:token:aab".getBytes(StandardCharsets.UTF_8));
        when(keyCommands.keys(any(byte[].class))).thenReturn(rawKeys);

        List<String> result = dao.searchData("satoken:login:token:", "aa", 0, 10, true);

        ArgumentCaptor<byte[]> patternCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(keyCommands).keys(patternCaptor.capture());
        assertThat(new String(patternCaptor.getValue(), StandardCharsets.UTF_8))
                .isEqualTo("satoken:login:token:*aa*");
        assertThat(result).containsExactly("satoken:login:token:aaa", "satoken:login:token:aab");

        // 分页语义与官方一致：start/size 截取
        assertThat(dao.searchData("satoken:login:token:", "aa", 1, 1, true))
                .containsExactly("satoken:login:token:aab");
    }
}

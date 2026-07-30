package com.frame.me.redis.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link RedisClient} 序列化约定测试.
 *
 * @author frame-me
 */
class RedisClientTest {

    private StringRedisTemplate template;
    private RedisClient client;

    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        client = new RedisClient(template, null);
    }

    /**
     * hSetAll 必须与 hSet 一样把值 JSON 序列化后再写入，
     * 否则非 String 值会被 StringRedisSerializer 抛 ClassCastException，
     * 且裸 String 值会让 hGet(clazz) 按 JSON 解析时炸掉.
     */
    @Test
    @SuppressWarnings("unchecked")
    void hSetAllShouldSerializeValuesToJson() {
        client.hSetAll("k", Map.of("s", "abc", "n", 42, "obj", Map.of("a", 1)));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        HashOperations<String, String, String> ops = template.opsForHash();
        verify(ops).putAll(eq("k"), captor.capture());
        Map<String, String> written = captor.getValue();
        assertThat(written.get("s")).isEqualTo("\"abc\"");
        assertThat(written.get("n")).isEqualTo("42");
        assertThat(written.get("obj")).isEqualTo("{\"a\":1}");
    }

    /**
     * 同一字段经 hSet / hSetAll 写入的存储形态必须一致，保证 hGet(clazz) 可读.
     */
    @Test
    void hSetAndHSetAllShouldProduceSameStorageForm() {
        client.hSet("k", "f", "abc");
        verify(template.opsForHash()).put("k", "f", "\"abc\"");

        client.hSetAll("k", Map.of("f", "abc"));
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(template.opsForHash()).putAll(eq("k"), captor.capture());
        assertThat(captor.getValue().get("f")).isEqualTo("\"abc\"");
    }
}

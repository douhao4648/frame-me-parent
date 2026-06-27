package com.frame.me.redis.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RStream;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RedissonTopic 单元测试.
 */
class RedissonTopicTest {

    @AfterEach
    void tearDown() {
        RedissonTopic.init(null);
    }

    @Test
    void shouldThrowWhenNotInitialized() {
        assertThrows(IllegalStateException.class, () -> RedissonTopic.getTopic("test"));
        assertThrows(IllegalStateException.class, () -> RedissonTopic.getPatternTopic("test"));
        assertThrows(IllegalStateException.class, () -> RedissonTopic.getReliableTopic("test"));
        assertThrows(IllegalStateException.class, () -> RedissonTopic.getStream("test"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnTopicObjects() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RPatternTopic patternTopic = mock(RPatternTopic.class);
        RReliableTopic reliableTopic = mock(RReliableTopic.class);
        RStream<Object, Object> stream = mock(RStream.class);

        when(client.getTopic("topic")).thenReturn(topic);
        when(client.getPatternTopic("pattern")).thenReturn(patternTopic);
        when(client.getReliableTopic("reliable")).thenReturn(reliableTopic);
        when(client.getStream("stream")).thenReturn(stream);

        RedissonTopic.init(client);

        assertNotNull(RedissonTopic.getTopic("topic"));
        assertNotNull(RedissonTopic.getPatternTopic("pattern"));
        assertNotNull(RedissonTopic.getReliableTopic("reliable"));
        assertNotNull(RedissonTopic.getStream("stream"));
    }
}

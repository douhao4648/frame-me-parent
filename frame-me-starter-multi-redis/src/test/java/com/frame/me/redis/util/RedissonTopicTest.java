package com.frame.me.redis.util;

import org.junit.jupiter.api.Test;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RStream;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RedissonTopic 单元测试.
 */
class RedissonTopicTest {

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

        RedissonTopic redissonTopic = new RedissonTopic(client);

        assertNotNull(redissonTopic.getTopic("topic"));
        assertNotNull(redissonTopic.getPatternTopic("pattern"));
        assertNotNull(redissonTopic.getReliableTopic("reliable"));
        assertNotNull(redissonTopic.getStream("stream"));
    }
}

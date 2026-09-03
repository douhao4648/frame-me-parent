package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SaTokenRedisUserValidator} 测试.
 *
 * @author frame-me
 */
class SaTokenRedisUserValidatorTest {

    @Test
    void existingTokenKey_returnsIdentity() {
        ReactiveStringRedisTemplate redis = mockRedis("satoken:login:token:abc123", "1001");
        SaTokenRedisUserValidator validator = new SaTokenRedisUserValidator(redis, new GatewayAuthProperties.SaToken());

        AuthIdentity identity = validator.validate("abc123").block();

        assertThat(identity).isNotNull();
        assertThat(identity.userId()).isEqualTo("1001");
        assertThat(identity.account()).isNull();
    }

    @Test
    void missingTokenKey_empty() {
        ReactiveStringRedisTemplate redis = mockRedis("satoken:login:token:gone", null);
        SaTokenRedisUserValidator validator = new SaTokenRedisUserValidator(redis, new GatewayAuthProperties.SaToken());

        assertThat(validator.validate("gone").blockOptional()).isEmpty();
    }

    @Test
    void customTokenNameAndLogicType_keyFollowsConfig() {
        GatewayAuthProperties.SaToken props = new GatewayAuthProperties.SaToken();
        props.setTokenName("satoken");
        props.setLogicType("sso");
        ReactiveStringRedisTemplate redis = mockRedis("satoken:sso:token:t1", "7");
        SaTokenRedisUserValidator validator = new SaTokenRedisUserValidator(redis, props);

        assertThat(validator.validate("t1").block()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static ReactiveStringRedisTemplate mockRedis(String expectedKey, String value) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(expectedKey)).thenReturn(Mono.justOrEmpty(value));
        return redis;
    }
}

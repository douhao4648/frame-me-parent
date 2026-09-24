package com.frame.me.sso;

import com.frame.me.sso.api.enums.AccessType;
import com.frame.me.sso.entity.AppEntity;
import com.frame.me.sso.infrastructure.config.SsoProperties;
import com.frame.me.sso.mapper.AppMapper;
import com.frame.me.sso.service.impl.AppServiceImpl;
import com.frame.me.sso.service.impl.AuthCodeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SSO 安全凭证熵与唯一性测试.
 *
 * <p>授权码 / 应用密钥均为 bearer 凭证，必须 256 位 CSPRNG（SecureRandom）生成：
 * 长度与字符集断言锁定"两段 simpleUUID 拼接"的实现不被退回 fastSimpleUUID
 * （ThreadLocalRandom，输出可被观测序列推导）.</p>
 *
 * @author frame-me
 */
class SsoCredentialEntropyTest {

    /**
     * 授权码为 64 字符十六进制（256 位），两次签发不相同.
     */
    @Test
    @SuppressWarnings("unchecked")
    void authCodeIs256BitAndUnique() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        AuthCodeServiceImpl service = new AuthCodeServiceImpl(redisTemplate, new SsoProperties());

        String code1 = service.issue("app-1", 1L, "openid", "http://client.local/cb");
        String code2 = service.issue("app-1", 1L, "openid", "http://client.local/cb");

        assertThat(code1).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(code2).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(code1).isNotEqualTo(code2);
    }

    /**
     * 应用密钥为 64 字符十六进制（256 位），两次注册不相同.
     */
    @Test
    void appSecretIs256BitAndUnique() {
        AppServiceImpl service = new AppServiceImpl(mock(AppMapper.class));

        AppEntity app1 = service.register("应用一", AccessType.INTERNAL,
                List.of("http://client.local/cb"), "openid");
        AppEntity app2 = service.register("应用二", AccessType.INTERNAL,
                List.of("http://client.local/cb"), "openid");

        assertThat(app1.getAppSecretPlain()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(app2.getAppSecretPlain()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(app1.getAppSecretPlain()).isNotEqualTo(app2.getAppSecretPlain());
    }
}

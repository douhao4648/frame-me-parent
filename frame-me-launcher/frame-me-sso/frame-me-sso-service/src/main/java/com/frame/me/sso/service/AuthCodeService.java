package com.frame.me.sso.service;

import cn.hutool.core.util.IdUtil;
import com.frame.me.sso.infrastructure.SsoConstant;
import com.frame.me.sso.infrastructure.config.SsoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * SSO 授权码服务.
 *
 * <p>Redis 为唯一存储（短时效 60s、一次性、原子防重放）。授权码 60s 过期，
 * Redis 故障期间整个登录流程都跑不了（sa-token 会话也在 Redis），DB 兜底无意义.</p>
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCodeService {

    private final StringRedisTemplate redisTemplate;
    private final SsoProperties properties;

    /**
     * 签发授权码.
     */
    public String issue(String appId, Long userId, String scopes, String redirectUri) {
        String code = IdUtil.fastSimpleUUID();
        Duration expires = properties.getAuthCode().getExpires();
        String key = SsoConstant.AUTH_CODE_KEY_PREFIX + code;
        // ponytail: value 用 ":" 分隔，redirectUri 含 ":" 时用 split(,4) 限分 4 段保证 redirectUri 完整
        String value = appId + ":" + userId + ":" + scopes + ":" + redirectUri;
        redisTemplate.opsForValue().set(key, value, expires);
        return code;
    }

    /**
     * 校验并消费授权码（一次性，原子操作）.
     *
     * @return null 表示无效或已使用
     */
    public CodePayload consume(String code) {
        String key = SsoConstant.AUTH_CODE_KEY_PREFIX + code;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        // 原子删除：只有持有者能消费
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.FALSE.equals(deleted)) {
            return null;
        }
        String[] parts = value.split(":", 4);
        CodePayload payload = new CodePayload();
        payload.appId = parts[0];
        payload.userId = Long.parseLong(parts[1]);
        payload.scopes = parts[2];
        payload.redirectUri = parts.length > 3 ? parts[3] : "";
        return payload;
    }

    /**
     * 授权码负载.
     */
    public static class CodePayload {

        public String appId;
        public Long userId;
        public String scopes;
        public String redirectUri;
    }
}

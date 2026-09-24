package com.frame.me.sso.service.impl;

import com.frame.me.sso.service.IAuthCodeService;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
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
public class AuthCodeServiceImpl implements IAuthCodeService {

    private final StringRedisTemplate redisTemplate;
    private final SsoProperties properties;

    /**
     * 签发授权码.
     *
     * <p>value 用 JSON 序列化：scopes/redirectUri 用户可控，":" 等分隔符拼接会被注入错位.</p>
     *
     * <p>授权码是 bearer 凭证，必须 CSPRNG：{@code simpleUUID}（Hutool 非 fast 版）底层是
     * {@link java.security.SecureRandom}；{@code fastSimpleUUID} 是 ThreadLocalRandom，
     * 输出可被外部观测序列推导，禁止用于安全凭证。两段拼接取 256 位熵（hex 天然 URL-safe）.</p>
     */
    @Override
    public String issue(String appId, Long userId, String scopes, String redirectUri) {
        String code = IdUtil.simpleUUID() + IdUtil.simpleUUID();
        Duration expires = properties.getAuthCode().getExpires();
        String key = SsoConstant.AUTH_CODE_KEY_PREFIX + code;
        CodePayload payload = new CodePayload();
        payload.appId = appId;
        payload.userId = userId;
        payload.scopes = scopes;
        payload.redirectUri = redirectUri;
        redisTemplate.opsForValue().set(key, JSON.toJSONString(payload), expires);
        return code;
    }

    /**
     * 只读查看授权码（不消费）：token 端点先 peek 校验应用/密钥/redirectUri，全部通过才 consume，
     * 校验失败不烧码（防恶意失效合法 code）.
     *
     * @return null 表示无效或已使用
     */
    @Override
    public CodePayload peek(String code) {
        String value = redisTemplate.opsForValue().get(SsoConstant.AUTH_CODE_KEY_PREFIX + code);
        if (value == null) {
            return null;
        }
        return JSON.parseObject(value, CodePayload.class);
    }

    /**
     * 校验并消费授权码（一次性，原子操作）.
     *
     * <p>{@code GETDEL}（Redis 6.2+）一次往返取值的同时删除，并发下只有一个请求能拿到
     * value，杜绝 GET+DELETE 两次往返的重放窗口.</p>
     *
     * @return null 表示无效或已使用
     */
    @Override
    public CodePayload consume(String code) {
        String key = SsoConstant.AUTH_CODE_KEY_PREFIX + code;
        String value = redisTemplate.opsForValue().getAndDelete(key);
        if (value == null) {
            return null;
        }
        return JSON.parseObject(value, CodePayload.class);
    }
}

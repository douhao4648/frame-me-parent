package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * sa-token 用户 token 验证器：直查下游共享 Redis 的 token key.
 *
 * <p>key 规则 {@code {tokenName}:{logicType}:token:{token}}（对齐 sa-token 原生
 * {@code StpLogic#splicingKeyTokenValue}，如 {@code satoken:login:token:xxx}），value 为 loginId
 * （sa-token 不透明 token 的 token→loginId 记录，字符串值）。key 存在即有效；
 * 踢人/登出删 key 后网关即时 401——吊销即时性优于 JWT 模式。</p>
 *
 * <p>用 {@link ReactiveStringRedisTemplate}（Lettuce reactive，Netty 实现）全链路非阻塞，
 * 与网关 event loop 同栈，无需 boundedElastic 线程切换——user 路由每请求一次校验，
 * 阻塞 API 在此不可接受。</p>
 *
 * <p>有意的边界：不模拟 sa-token 的 active-timeout 滑动续期语义（网关只做粗筛，
 * 精确会话语义归下游）；要求下游与网关共用同一 Redis 实例（不支持多 Redis，
 * multi-redis 依赖 base 会拖入 Servlet 栈）。token key 的 value 只有 loginId，
 * 故 {@link AuthIdentity#account()} 恒为 null——网关只注入 {@code X-User-Id}，
 * 下游需要 account 时自行验透传 token 或按 id 回源。</p>
 *
 * @author frame-me
 */
@Slf4j
public class SaTokenRedisUserValidator implements IUserValidator {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public SaTokenRedisUserValidator(ReactiveStringRedisTemplate redisTemplate, GatewayAuthProperties.SaToken properties) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = properties.getTokenName() + ":" + properties.getLogicType() + ":token:";
    }

    @Override
    public Mono<AuthIdentity> validate(String token) {
        return redisTemplate.opsForValue().get(keyPrefix + token)
                .filter(loginId -> !loginId.isBlank())
                .map(loginId -> new AuthIdentity(loginId, null));
    }
}

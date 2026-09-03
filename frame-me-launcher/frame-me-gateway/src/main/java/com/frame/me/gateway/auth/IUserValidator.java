package com.frame.me.gateway.auth;

import reactor.core.publisher.Mono;

/**
 * 用户 token 验证器（user 路由）.
 *
 * <p>实现按 {@code me.gateway.auth.user-validator} 条件装配：
 * {@link JwtUserValidator}（jwt）/ {@link SaTokenRedisUserValidator}（sa-token）.</p>
 *
 * @author frame-me
 */
public interface IUserValidator {

    /**
     * 校验用户 token.
     *
     * @param token 已剥离前缀的 token
     * @return 有效返回身份，无效/过期返回 {@code Mono.empty()}
     */
    Mono<AuthIdentity> validate(String token);
}

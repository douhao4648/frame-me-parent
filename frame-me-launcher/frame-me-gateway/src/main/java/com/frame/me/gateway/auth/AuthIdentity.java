package com.frame.me.gateway.auth;

/**
 * 认证通过后的用户身份（网关注入下游身份头的来源）.
 *
 * @param userId  用户 ID（注入 {@code X-User-Id}）
 * @param account 账号，可为 {@code null}（非 null 时注入 {@code X-User-Account}）
 * @author frame-me
 */
public record AuthIdentity(String userId, String account) {
}

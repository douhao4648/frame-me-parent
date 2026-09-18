package com.frame.me.auth.spi;

/**
 * 上游身份源明确判定用户失效（被踢/禁用/串号）时由
 * {@link IAuthUserDetailsService#loadUserById} 抛出.
 *
 * <p>与返回 {@code null} 的语义区分：{@code null} 表示「暂时无法确认」
 * （如 SSO 不可达、本地缓存 miss），调用方可用 token 快照兜底保可用性；
 * 本异常表示「上游明确说了这个用户不行」，调用方必须 fail-closed（401），
 * 不得走快照重建——否则"数据库已禁用"不等于"会话失效"。</p>
 *
 * @author frame-me
 */
public class UpstreamUserInvalidException extends RuntimeException {

    public UpstreamUserInvalidException(String message) {
        super(message);
    }
}

package com.frame.me.auth.core;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;
import java.util.UUID;

/**
 * 账号密码认证器（JWT / Sa-Token 等认证实现共用）.
 *
 * <p>承载「查用户 → 空则 4001 → 校验密码 → 失败 4001」的共享认证步骤，
 * 各 {@code IAuthService} 实现只需在此之后做凭证签发/会话建立。</p>
 *
 * @author frame-me
 */
public final class AuthUserAuthenticator {

    /**
     * 哑密码 hash：用户不存在时用于执行同等耗时的密码校验，
     * 消除「账号不存在快速 401 / 账号存在慢速 401」的响应时间差（账号枚举 oracle）。
     * 对齐 Spring Security {@code DaoAuthenticationProvider} 的 userNotFoundPassword 机制.
     *
     * <p>每个认证器实例生成独立的哑 hash（而非固定公开字符串），
     * 消除「固定 hash 被识别即判断账号不存在」的理论风险.</p>
     */
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthUserAuthenticator(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.dummyPasswordHash = passwordEncoder.encode("dummy-" + UUID.randomUUID());
    }

    /**
     * 校验账号密码，成功返回用户，失败抛 401.
     *
     * @param userDetailsService 业务用户服务
     * @param account            登录账号
     * @param rawPassword        原始密码
     * @return 认证通过的用户
     * @throws BusinessException 账号不存在或密码错误（统一 4001「账号或密码错误」，凭证错误与会话缺失 401 区分）
     */
    public User authenticate(IAuthUserDetailsService userDetailsService, String account, String rawPassword) {
        User user = userDetailsService.loadUserByAccount(account);
        // 用户不存在也对哑 hash 执行同等耗时的校验，避免响应时间差泄漏账号是否存在
        String passwordHash = user != null ? user.getPassword() : dummyPasswordHash;
        boolean matched = passwordEncoder.matches(rawPassword, passwordHash);
        if (user == null || !matched) {
            // 凭证错误（4001）：登录流程本身失败，前端留登录页显示错误，避免与"会话缺失"401 混淆导致循环重定向
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "账号或密码错误");
        }
        if (!User.STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "账号已被禁用");
        }
        return user;
    }
}

package com.frame.me.auth.core;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;

/**
 * 账号密码认证器（JWT / Sa-Token 等认证实现共用）.
 *
 * <p>承载「查用户 → 空则 401 → 校验密码 → 失败 401」的共享认证步骤，
 * 各 {@code IAuthService} 实现只需在此之后做凭证签发/会话建立。</p>
 *
 * <p>实现为静态工具而非接口 default 方法：业务侧的
 * {@link IAuthUserDetailsService} 多为 Mockito mock，default 方法会被一并 mock 掉。</p>
 *
 * @author frame-me
 */
public final class AuthUserAuthenticator {

    /**
     * 哑 BCrypt hash：用户不存在时用于执行同等耗时的密码校验，
     * 消除「账号不存在快速 401 / 账号存在慢速 401」的响应时间差（账号枚举 oracle）。
     * 对齐 Spring Security {@code DaoAuthenticationProvider} 的 userNotFoundPassword 机制.
     *
     * <p>每次 JVM 启动生成随机盐的哑 hash（而非固定公开字符串），
     * 消除「固定 hash 被识别即判断账号不存在」的理论风险.</p>
     */
    private static final String DUMMY_BCRYPT_HASH = com.frame.me.auth.util.PasswordUtils.encode("dummy-" + java.util.UUID.randomUUID());

    private AuthUserAuthenticator() {
    }

    /**
     * 校验账号密码，成功返回用户，失败抛 401.
     *
     * @param userDetailsService 业务用户服务
     * @param account            登录账号
     * @param rawPassword        原始密码
     * @return 认证通过的用户
     * @throws BusinessException 账号不存在或密码错误（统一 401「账号或密码错误」）
     */
    public static User authenticate(IAuthUserDetailsService userDetailsService, String account, String rawPassword) {
        User user = userDetailsService.loadUserByAccount(account);
        // 用户不存在也对哑 hash 执行同等耗时的校验，避免响应时间差泄漏账号是否存在
        String passwordHash = user != null ? user.getPassword() : DUMMY_BCRYPT_HASH;
        boolean matched = userDetailsService.matches(rawPassword, passwordHash);
        if (user == null || !matched) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        return user;
    }
}

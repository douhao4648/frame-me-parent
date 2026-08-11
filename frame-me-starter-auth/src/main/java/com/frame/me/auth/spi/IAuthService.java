package com.frame.me.auth.spi;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;

/**
 * 认证服务接口.
 *
 * <p>定义登录、登出、刷新 Token 等核心行为。具体实现由后续认证模块提供，
 * 例如 JWT、Sa-Token、Spring Security 等。</p>
 *
 * @author frame-me
 */
public interface IAuthService {

    /**
     * 用户登录.
     *
     * @param account  登录账号
     * @param password 登录密码
     * @return 登录成功后的用户凭证（如 Token、SessionId）
     */
    String login(String account, String password);

    /**
     * 按已知用户直接建立会话（RP 场景：身份已由外部 IdP 验证，无需密码校验）.
     *
     * <p>供 SSO 下游等"code 换用户后建本地 session"场景使用——{@link #login(String, String)}
     * 走账号密码流程，不适用于 RP。具体认证实现按需覆盖（如 Sa-Token 的
     * {@code StpUtil.login} + 用户快照缓存）；默认抛异常表示不支持 RP 场景。</p>
     *
     * @param user 已认证用户（由外部 IdP 提供身份，id 必填）
     * @return 会话凭证（Token、SessionId）
     * @throws BusinessException 默认实现抛 {@code UNAUTHORIZED}，表示当前认证实现不支持 RP
     */
    default String loginByUser(User user) {
        throw new BusinessException(ResultCode.UNAUTHORIZED, "当前认证实现不支持按已知用户直接建立会话（RP 场景）");
    }

    /**
     * 用户登出.
     *
     * @param credential 用户凭证
     */
    void logout(String credential);

    /**
     * 根据用户 ID 强制登出（踢出）该用户的所有会话.
     *
     * <p>默认空实现；具体认证模块按需覆盖。例如 Sa-Token 可注销 loginId 的全部 session，
     * JWT 可删除该用户的 Refresh Token 使其无法续期。</p>
     *
     * @param userId 用户 ID
     */
    default void logoutByUserId(Long userId) {
        // 默认空实现，避免破坏现有实现
    }

    /**
     * 刷新凭证.
     *
     * @param credential 原凭证
     * @return 新凭证
     */
    String refresh(String credential);

    /**
     * 校验凭证是否有效.
     *
     * @param credential 用户凭证
     * @return 有效返回 {@code true}，否则返回 {@code false}
     */
    boolean validate(String credential);

    /**
     * 根据凭证获取用户信息.
     *
     * @param credential 用户凭证
     * @return 用户信息，无效时返回 {@code null}
     */
    User getUser(String credential);
}

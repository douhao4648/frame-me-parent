package com.frame.me.auth.jwt.core;

import com.frame.me.base.user.User;

/**
 * 用户详情服务接口.
 *
 * <p>业务工程需实现此接口，提供根据账号/ID 查询用户以及密码校验能力。</p>
 *
 * @author frame-me
 */
public interface IAuthUserDetailsService {

    /**
     * 根据账号查询用户.
     *
     * @param account 登录账号
     * @return 用户信息，不存在时返回 {@code null}
     */
    User loadUserByAccount(String account);

    /**
     * 根据用户 ID 查询用户.
     *
     * @param id 用户 ID
     * @return 用户信息，不存在时返回 {@code null}
     */
    User loadUserById(Long id);

    /**
     * 校验原始密码与加密密码是否匹配.
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 匹配返回 {@code true}
     */
    boolean matches(String rawPassword, String encodedPassword);
}

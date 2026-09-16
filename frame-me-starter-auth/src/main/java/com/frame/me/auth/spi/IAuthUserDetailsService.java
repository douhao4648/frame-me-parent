package com.frame.me.auth.spi;

import com.frame.me.base.user.User;

/**
 * 用户详情服务接口.
 *
 * <p>业务工程实现此接口提供用户查询，密码校验由容器中的 PasswordEncoder 统一完成。</p>
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

}

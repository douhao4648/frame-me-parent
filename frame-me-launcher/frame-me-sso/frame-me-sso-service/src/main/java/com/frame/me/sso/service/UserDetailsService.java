package com.frame.me.sso.service;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.user.User;
import com.frame.me.sso.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SSO 用户详情服务，实现认证 SPI.
 *
 * <p>把 UserEntity 实体转为基础 User 基类，供 sa-token 会话治理使用。</p>
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class UserDetailsService implements IAuthUserDetailsService {

    private final UserService userService;

    @Override
    public User loadUserByAccount(String account) {
        UserEntity userEntity = userService.findByAccount(account);
        return userEntity == null ? null : toUser(userEntity);
    }

    @Override
    public User loadUserById(Long id) {
        UserEntity userEntity = userService.findById(id);
        return userEntity == null ? null : toUser(userEntity);
    }

    private User toUser(UserEntity userEntity) {
        User user = new User();
        user.setId(userEntity.getId());
        user.setAccount(userEntity.getAccount());
        user.setPassword(userEntity.getPassword());
        user.setNickname(userEntity.getName());
        user.setStatus("ACTIVE".equals(userEntity.getStatus())
                ? User.STATUS_ENABLED : User.STATUS_DISABLED);
        return user;
    }
}

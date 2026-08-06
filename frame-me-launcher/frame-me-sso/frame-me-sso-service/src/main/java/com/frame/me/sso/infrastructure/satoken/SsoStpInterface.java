package com.frame.me.sso.infrastructure.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * sa-token 权限/角色源，读 UserEntity.roles.
 *
 * @author frame-me
 */
@Component
@RequiredArgsConstructor
public class SsoStpInterface implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        UserEntity user = userMapper.selectOneById(Long.parseLong(loginId.toString()));
        if (user == null || user.getRoles() == null || user.getRoles().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(user.getRoles().split(","));
    }
}

package com.frame.me.sso.infrastructure.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * sa-token 权限/角色源，读 UserEntity.roles.
 *
 * <p>走 { IUserService#findById}（L1/L2 缓存）：{@code @SaCheckRole}
 * 每次检查都触发本方法，是角色数据的高频读路径。</p>
 *
 * @author frame-me
 */
@Component
@RequiredArgsConstructor
public class SsoStpInterface implements StpInterface {

    private final IUserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (SsoTokenUtils.isAppLoginId(loginId)) {
            // 应用 token（client_credentials）无用户角色，fail-closed 空列表
            return Collections.emptyList();
        }
        UserEntity user;
        try {
            user = userService.findById(Long.parseLong(loginId.toString()));
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
        if (user == null || user.getRoles() == null || user.getRoles().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(user.getRoles().split(","));
    }
}

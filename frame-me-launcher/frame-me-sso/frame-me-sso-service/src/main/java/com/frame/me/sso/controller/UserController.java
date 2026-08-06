package com.frame.me.sso.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sso.api.IUserApi;
import com.frame.me.sso.api.vo.UserInfoVO;
import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 用户资源端点，实现 {@link IUserApi}（@SaCheckRole admin 保护）.
 *
 * <p>用户域操作（踢人等），与应用资源 {@link AppController} 分离.</p>
 *
 * @author frame-me
 */
@RestController
@RequiredArgsConstructor
public class UserController implements IUserApi {

    private final UserService userService;

    /**
     * 用户信息端点.
     *
     * <p>下游凭 token 调用，SSO 用 {@link StpUtil#getLoginIdByToken} 原生验 token（查 sa-token Redis），
     * 通过后从 userId 现查 {@code sso_user} 返回 account/name/roles. 无 JWT 验签代码.</p>
     */
    @Override
    public IResult<UserInfoVO> userinfo(@RequestHeader("Authorization") String auth) {
        String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return Result.error(ResultCode.UNAUTHORIZED, "token 无效或已过期");
        }
        Long userId = Long.parseLong(loginId.toString());
        UserEntity userEntity = userService.findById(userId);
        if (userEntity == null || !"ACTIVE".equals(userEntity.getStatus())) {
            return Result.error(ResultCode.UNAUTHORIZED, "用户不存在或已禁用");
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setSub(String.valueOf(userEntity.getId()));
        vo.setAccount(userEntity.getAccount());
        vo.setName(userEntity.getName());
        vo.setRoles(userEntity.getRoles());
        return Result.success(vo);
    }
}

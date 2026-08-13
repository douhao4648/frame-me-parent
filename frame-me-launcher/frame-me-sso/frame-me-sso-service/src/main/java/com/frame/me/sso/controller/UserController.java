package com.frame.me.sso.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.util.PasswordUtils;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sso.api.IUserApi;
import com.frame.me.sso.api.dto.UserCreateDTO;
import com.frame.me.sso.api.dto.UserUpdateDTO;
import com.frame.me.sso.api.vo.UserInfoVO;
import com.frame.me.sso.api.vo.UserVO;
import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.infrastructure.satoken.SsoStpUtil;
import com.frame.me.sso.infrastructure.satoken.SsoTokenUtils;
import com.frame.me.sso.service.ILogoutService;
import com.frame.me.sso.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SSO 用户资源端点，实现 {@link IUserApi}.
 *
 * <p>{@code /info} 下游凭 token 取用户信息（匿名 + 自验 token）；
 * 其余为用户 CRUD 管理端点（{@code @SaCheckRole("admin")} + 设备闸仅认 SSO 登录会话）.
 * 踢人归 {@code AuthController}（{@code /api/auth} 前缀），应用资源归 {@link AppController}.</p>
 *
 * @author frame-me
 */
@RestController
@RequiredArgsConstructor
public class UserController implements IUserApi {

    private final IUserService userService;
    private final ILogoutService logoutService;

    /**
     * 用户信息端点.
     *
     * <p>下游凭 token 调用，SSO 用 {@code SsoStpUtil.stpLogic.getLoginIdByToken} 原生验 token
     * （查 sa-token Redis 的 {@code sso} 账号体系），通过后从 userId 现查 {@code sso_user}
     * 返回 account/name/roles. 无 JWT 验签代码.</p>
     *
     * <p>{@code @Anonymous}：下游服务端调用带的是 {@code Authorization: Bearer} 的 SSO app token，
     * 不是浏览器会话 token，过不了 {@code AuthFilter} 的登录校验；本端点自验 token，
     * 无效返 401，fail-closed 语义不变.</p>
     */
    @Anonymous
    @Override
    public IResult<UserInfoVO> userinfo(@RequestHeader("Authorization") String auth) {
        long userId = SsoTokenUtils.requireBearerUserId(auth);
        UserEntity userEntity = userService.findById(userId);
        if (userEntity == null || !"ACTIVE".equals(userEntity.getStatus())) {
            return Result.error(ResultCode.BAD_CREDENTIAL, "用户不存在或已禁用");
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setSub(String.valueOf(userEntity.getId()));
        vo.setAccount(userEntity.getAccount());
        vo.setName(userEntity.getName());
        vo.setRoles(userEntity.getRoles());
        return Result.success(vo);
    }

    /**
     * 创建用户：账号唯一；密码 BCrypt 加密入库（强度见 {@code me.auth.password.bcrypt-strength}）.
     */
    @SaCheckRole(value = "admin", type = SsoStpUtil.TYPE)
    @Override
    public IResult<UserVO> createUser(UserCreateDTO dto) {
        if (userService.findByAccount(dto.getAccount()) != null) {
            return Result.error(ResultCode.ERROR, "账号已存在");
        }
        UserEntity user = new UserEntity();
        user.setAccount(dto.getAccount());
        user.setPassword(PasswordUtils.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setStatus("ACTIVE");
        user.setRoles(dto.getRoles() != null ? dto.getRoles() : "");
        userService.save(user);
        return Result.success(toVO(user));
    }

    /**
     * 用户列表（不含密码字段）.
     */
    @SaCheckRole(value = "admin", type = SsoStpUtil.TYPE)
    @Override
    public IResult<List<UserVO>> listUsers() {
        return Result.success(userService.list().stream()
                .map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 用户详情.
     */
    @SaCheckRole(value = "admin", type = SsoStpUtil.TYPE)
    @Override
    public IResult<UserVO> getUser(Long id) {
        UserEntity user = userService.findById(id);
        if (user == null) {
            return Result.error(ResultCode.ERROR, "用户不存在");
        }
        return Result.success(toVO(user));
    }

    /**
     * 更新用户（字段 null 表示不更新）.
     *
     * <p>改密码或置 DISABLED 联动踢出该用户全部会话——即时生效，不等存量 token
     * 自然过期（与 disableApp 同策）。防自锁：不能禁用当前登录账号.</p>
     */
    @SaCheckRole(value = "admin", type = SsoStpUtil.TYPE)
    @Override
    public IResult<Boolean> updateUser(Long id, UserUpdateDTO dto) {
        UserEntity user = userService.findById(id);
        if (user == null) {
            return Result.error(ResultCode.ERROR, "用户不存在");
        }
        if ("DISABLED".equals(dto.getStatus()) && SsoStpUtil.stpLogic.getLoginIdAsLong() == id) {
            return Result.error(ResultCode.ERROR, "不能禁用当前登录账号");
        }
        boolean kick = false;
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        if (dto.getRoles() != null) {
            user.setRoles(dto.getRoles());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(PasswordUtils.encode(dto.getPassword()));
            kick = true;
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
            if ("DISABLED".equals(dto.getStatus())) {
                kick = true;
            }
        }
        userService.update(user);
        if (kick) {
            logoutService.logout(id, null,
                    "DISABLED".equals(dto.getStatus()) ? "user-disabled" : "password-changed");
        }
        return Result.success(true);
    }

    /**
     * 删除用户（逻辑删除）：联动踢出全部会话，账号不可再登录。防自锁：不能删除当前登录账号.
     */
    @SaCheckRole(value = "admin", type = SsoStpUtil.TYPE)
    @Override
    public IResult<Boolean> deleteUser(Long id) {
        UserEntity user = userService.findById(id);
        if (user == null) {
            return Result.error(ResultCode.ERROR, "用户不存在");
        }
        if (SsoStpUtil.stpLogic.getLoginIdAsLong() == id) {
            return Result.error(ResultCode.ERROR, "不能删除当前登录账号");
        }
        userService.delete(id);
        logoutService.logout(id, null, "user-deleted");
        return Result.success(true);
    }

    private UserVO toVO(UserEntity user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setName(user.getName());
        vo.setStatus(user.getStatus());
        vo.setRoles(user.getRoles());
        return vo;
    }
}

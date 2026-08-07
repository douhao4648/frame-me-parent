package com.frame.me.sso.api;

import com.frame.me.api.result.IResult;
import com.frame.me.sso.api.dto.UserCreateDTO;
import com.frame.me.sso.api.dto.UserUpdateDTO;
import com.frame.me.sso.api.vo.UserInfoVO;
import com.frame.me.sso.api.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * SSO 用户资源 API 契约.
 *
 * <p>{@code /info} 是下游凭 token 取用户信息的匿名端点（自验 token）；
 * 其余为用户 CRUD 管理端点，需 admin 角色 + 默认设备会话（设备闸）.</p>
 *
 * <p>服务端 {@code UserController implements IUserApi}，
 * 下游管理工具可用 HTTP Interface 代理调用.</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 用户管理", description = "用户信息端点 + 用户 CRUD（管理端点需 admin）")
@HttpExchange("/api/users")
public interface IUserApi {

    /**
     * 用户信息端点：下游凭 token 调用，SSO 原生验 token 后返回用户信息.
     *
     * @param auth Authorization 头，形如 "Bearer &lt;token&gt;" 或裸 token
     * @return 用户信息，token 无效返回 401
     */
    @Operation(summary = "用户信息", description = "下游凭 token 调用，SSO 原生验 token 后返回用户信息")
    @GetExchange("/info")
    IResult<UserInfoVO> userinfo(@RequestHeader("Authorization") String auth);

    /**
     * 创建用户（集合根创建，admin）.
     */
    @Operation(summary = "创建用户", description = "账号唯一；密码明文提交，服务端 BCrypt 加密入库")
    @PostExchange("/")
    IResult<UserVO> createUser(@Valid @RequestBody UserCreateDTO dto);

    /**
     * 用户列表（集合根查询，admin）.
     */
    @Operation(summary = "用户列表", description = "列出全部用户（不含密码字段）")
    @GetExchange("/")
    IResult<List<UserVO>> listUsers();

    /**
     * 用户详情（admin）.
     */
    @Operation(summary = "用户详情", description = "按用户 ID 查询")
    @GetExchange("/{id}")
    IResult<UserVO> getUser(@Parameter(description = "用户 ID", required = true) @PathVariable Long id);

    /**
     * 更新用户（字段 null 表示不更新，admin）.
     *
     * <p>改密码或置 DISABLED 会联动踢出该用户全部会话（即时生效）.</p>
     */
    @Operation(summary = "更新用户", description = "更新用户名/密码/角色/状态；改密码或禁用联动踢出全部会话")
    @PostExchange("/{id}")
    IResult<Boolean> updateUser(@Parameter(description = "用户 ID", required = true) @PathVariable Long id,
                                @Valid @RequestBody UserUpdateDTO dto);

    /**
     * 删除用户（逻辑删除，admin）：联动踢出该用户全部会话，账号不可再登录.
     */
    @Operation(summary = "删除用户", description = "逻辑删除并联动踢出全部会话（删除即生效）")
    @DeleteExchange("/{id}")
    IResult<Boolean> deleteUser(@Parameter(description = "用户 ID", required = true) @PathVariable Long id);
}

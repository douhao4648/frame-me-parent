package com.frame.me.sso.api;

import com.frame.me.api.result.IResult;
import com.frame.me.sso.api.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * SSO 用户资源 API 契约（需 admin 角色）.
 *
 * <p>用户域操作（踢人等），与 {@link IAppApi} 应用域分离，REST 资源归属独立.</p>
 *
 * <p>服务端 {@code UserController implements IUserApi}，
 * 下游管理工具可用 HTTP Interface 代理调用.</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 用户管理", description = "强制登出等用户域操作")
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


}

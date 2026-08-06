package com.frame.me.sso.api;

import com.frame.me.api.result.IResult;
import com.frame.me.sso.api.dto.TokenRequestDTO;
import com.frame.me.sso.api.vo.TokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * SSO 授权码流程 API 契约.
 *
 * <p>下游应用引本 api 模块，既可用 Spring HTTP Interface 生成客户端代理调用 SSO，
 * 也可作为服务端 Controller 的路由契约（{@code AuthController implements IAuthApi}）.</p>
 *
 * <p>路由声明与参数绑定注解在本接口统一维护，服务端方法仅标 {@code @Override}、
 * 安全注解（{@code @Anonymous}）留 impl，与 {@link IAppApi} 同模式.</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 授权", description = "授权码流程、token 颁发")
@HttpExchange("/api/auth")
public interface IAuthApi {

    /**
     * 授权端点：校验应用 → 未登录重定向登录页 → 已登录发 code 回调.
     *
     * @param appId       应用 ID
     * @param redirectUri 回调地址
     * @param scope       授权范围
     * @param nonce       防重放随机串
     * @return 302 重定向（登录页或回调地址），应用非法/redirect 不在白名单返回 400
     */
    @Operation(summary = "授权端点", description = "校验应用→未登录重定向登录页→已登录发 code 回调")
    @GetExchange("/authorize")
    ResponseEntity<Void> authorize(@RequestParam String appId,
                                   @RequestParam String redirectUri,
                                   @RequestParam(required = false, defaultValue = "openid") String scope,
                                   @RequestParam(required = false) String nonce);

    /**
     * 登录页：重定向到 SSO 登录页，或提示 POST /api/auth/login.
     *
     * @return 302 重定向到登录页，或 200 JSON 提示
     */
    @Operation(summary = "登录页", description = "重定向到 SSO 登录页或提示 POST /api/auth/login")
    @GetExchange("/login-page")
    ResponseEntity<Object> loginPage();

    /**
     * 换 token：授权码 → sa-token 不透明 token.
     *
     * @param request 换 token 请求
     * @return token 响应
     */
    @Operation(summary = "换 token", description = "授权码换 sa-token 不透明 token，下游凭此调 /userinfo")
    @PostExchange("/token")
    IResult<TokenVO> token(@Valid @RequestBody TokenRequestDTO request);

    /**
     * 强制登出用户（踢人）.
     *
     * @param userId 用户 ID
     * @param appId  应用 ID，可选，null 表示踢所有应用
     * @param reason 踢人原因
     * @return 发 {@code UserLogoutEvent} 供下游订阅清 session
     */
    @Operation(summary = "强制登出", description = "强制登出用户，发 UserLogoutEvent 供下游订阅清 session")
    @PostExchange("/{userId}/logout")
    IResult<Boolean> forceLogout(@Parameter(description = "用户 ID", required = true) @PathVariable Long userId,
                                 @Parameter(description = "应用 ID，可选，null 表示踢所有应用") @RequestParam(required = false) String appId,
                                 @Parameter(description = "踢人原因") @RequestParam(defaultValue = "admin") String reason);
}

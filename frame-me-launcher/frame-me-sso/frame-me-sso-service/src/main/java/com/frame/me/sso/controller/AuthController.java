package com.frame.me.sso.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sso.api.IAuthApi;
import com.frame.me.sso.api.dto.TokenRequestDTO;
import com.frame.me.sso.api.vo.TokenVO;
import com.frame.me.sso.entity.AppEntity;
import com.frame.me.sso.infrastructure.config.SsoProperties;
import com.frame.me.sso.infrastructure.satoken.SsoTokenUtils;
import com.frame.me.sso.service.AppService;
import com.frame.me.sso.service.AuthCodeService;
import com.frame.me.sso.service.LogoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * SSO 授权码流程端点，实现 {@link IAuthApi}.
 *
 * <p>token 用 sa-token 不透明 token（{@link StpUtil#createLoginSession}），
 * 验 token 用 {@link StpUtil#getLoginIdByToken}（查 sa-token Redis），无 JWT 验签代码.
 * 下游拿 token 调 {@code /userinfo} 取用户信息，SSO 原生验 token.</p>
 *
 * <p>登录/登出/续期复用 starter-sa-token 的 {@code /api/auth/*} 端点，本类不重复定义.</p>
 *
 * <p>路由声明与参数绑定注解在 {@link IAuthApi} 接口上统一维护，本类方法仅标
 * {@code @Override}，安全注解（{@code @Anonymous}）留 impl，与 {@code AdminController} 同模式.</p>
 *
 * @author frame-me
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController implements IAuthApi {

    private final AppService appService;
    private final AuthCodeService authCodeService;
    private final SsoProperties properties;
    private final LogoutService logoutService;

    /**
     * 授权端点：校验应用 → 校验 scope 白名单 → 未登录重定向登录页 → 已登录发 code 回调.
     *
     * <p>{@code state} 是 OAuth 防登录 CSRF 的基础参数：下游生成、SSO 原样回显在回调里，
     * 下游比对一致才接受回调。SSO 侧只做透传（经登录页跳转链不丢失），不存储.</p>
     */
    @Anonymous
    @Override
    public ResponseEntity<Void> authorize(@RequestParam String appId,
                                          @RequestParam String redirectUri,
                                          @RequestParam(required = false, defaultValue = "openid") String scope,
                                          @RequestParam(required = false) String nonce,
                                          @RequestParam(required = false) String state) {
        AppEntity app = appService.findByAppId(appId);
        if (app == null || !"ACTIVE".equals(app.getStatus())) {
            // 显式状态码 + Result body（GlobalExceptionHandler 透传），三个 400 分支靠 message 区分
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "应用不存在或已禁用");
        }
        if (!appService.isRedirectAllowed(app, redirectUri)) {
            // redirectUri 校验失败时禁止重定向回跳（RFC 6749 §4.1.2.1），只能原地报错
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirectUri 不在应用白名单");
        }
        if (!appService.isScopeAllowed(app, scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 超出应用授权范围");
        }
        if (!StpUtil.isLogin()) {
            String target = "/sso-login.html?redirect=" +
                    URLEncoder.encode("/api/auth/authorize?" +
                            buildQuery(appId, redirectUri, scope, nonce, state), StandardCharsets.UTF_8);
            return ResponseEntity.status(302).location(URI.create(target)).build();
        }
        Long userId = StpUtil.getLoginIdAsLong();
        String code = authCodeService.issue(appId, userId, scope, redirectUri);
        String sep = redirectUri.contains("?") ? "&" : "?";
        String location = redirectUri + sep + "code=" + code;
        if (state != null && !state.isBlank()) {
            location += "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(302).location(URI.create(location)).build();
    }

    /**
     * 登录页.
     */
    @Anonymous
    @Override
    public ResponseEntity<Object> loginPage() {
        if (properties.getLoginPage().isEnabled()) {
            return ResponseEntity.status(302).location(URI.create("/sso-login.html")).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":200,\"msg\":\"请 POST /base/auth/login\"}");
    }

    /**
     * 换 token：按 {@code grantType} 分派.
     *
     * <p>用 {@link StpUtil#createLoginSession} 而非 {@link StpUtil#login}：本端点由下游服务端
     * POST 调用，无浏览器请求上下文，{@code createLoginSession} 不依赖上下文纯建会话返 token.
     * {@code is-concurrent: true} 保证与浏览器会话独立.</p>
     *
     * <ul>
     *   <li>{@code authorization_code}（默认）：code → 用户 token（loginId=userId，deviceType=appId），
     *       下游凭此调 /userinfo</li>
     *   <li>{@code client_credentials}：appId+appSecret → 应用 token（loginId="app:"+appId，
     *       无用户维度），用于定时任务/服务间等机器对机器调用</li>
     * </ul>
     */
    @Anonymous
    @Override
    public IResult<TokenVO> token(@Valid @RequestBody TokenRequestDTO req) {
        String grantType = req.getGrantType() == null || req.getGrantType().isBlank()
                ? "authorization_code" : req.getGrantType();
        return switch (grantType) {
            case "authorization_code" -> authorizationCodeGrant(req);
            case "client_credentials" -> clientCredentialsGrant(req);
            default -> Result.error(ResultCode.BAD_REQUEST,
                    "grantType 仅支持 authorization_code / client_credentials");
        };
    }

    /**
     * 授权码模式：code → 用户 token.
     */
    private IResult<TokenVO> authorizationCodeGrant(TokenRequestDTO req) {
        if (req.getCode() == null || req.getCode().isBlank()
                || req.getRedirectUri() == null || req.getRedirectUri().isBlank()) {
            return Result.error(ResultCode.BAD_REQUEST, "authorization_code 模式需 code 与 redirectUri");
        }
        AuthCodeService.CodePayload payload = authCodeService.consume(req.getCode());
        if (payload == null) {
            return Result.error(ResultCode.UNAUTHORIZED, "授权码无效或已使用");
        }
        if (!payload.appId.equals(req.getAppId())) {
            return Result.error(ResultCode.UNAUTHORIZED, "app_id 与授权码不匹配");
        }
        AppEntity app = appService.findByAppId(req.getAppId());
        if (app == null || !"ACTIVE".equals(app.getStatus())) {
            return Result.error(ResultCode.UNAUTHORIZED, "应用不存在或已禁用");
        }
        if (!payload.redirectUri.equals(req.getRedirectUri())) {
            return Result.error(ResultCode.UNAUTHORIZED, "redirect_uri 不匹配");
        }
        if (!appService.verifySecret(app, req.getAppSecret())) {
            return Result.error(ResultCode.UNAUTHORIZED, "密钥校验失败");
        }
        return Result.success(issueToken(payload.userId, payload.appId));
    }

    /**
     * 客户端凭证模式：appId+appSecret → 应用 token（主体是应用自身，无用户维度）.
     *
     * <p>loginId 用 {@code "app:"+appId} 字符串，与用户 token 的数字 userId 区分；
     * 该 token 调 /userinfo 会 401（无对应用户），只应用于机器对机器调用.
     * INTERNAL / EXTERNAL 均强制校验 appSecret（注册时两类型都发密钥）.</p>
     */
    private IResult<TokenVO> clientCredentialsGrant(TokenRequestDTO req) {
        AppEntity app = appService.findByAppId(req.getAppId());
        if (app == null || !"ACTIVE".equals(app.getStatus())) {
            return Result.error(ResultCode.UNAUTHORIZED, "应用不存在或已禁用");
        }
        if (!appService.verifySecret(app, req.getAppSecret())) {
            return Result.error(ResultCode.UNAUTHORIZED, "密钥校验失败");
        }
        return Result.success(issueToken(SsoTokenUtils.appLoginId(app.getAppId()), app.getAppId()));
    }

    /**
     * 签发 sa-token 不透明 token：app 维度（deviceType=appId）+ 独立 TTL.
     */
    private TokenVO issueToken(Object loginId, String appId) {
        long timeout = properties.getToken().getAppTimeout().getSeconds();
        String token = StpUtil.createLoginSession(loginId,
                new SaLoginParameter()
                        .setDeviceType(appId)
                        .setTimeout(timeout));
        TokenVO vo = new TokenVO();
        vo.setAccessToken(token);
        return vo;
    }

    /**
     * 强制登出用户（踢人）.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<Boolean> forceLogout(Long userId, String appId, String reason) {
        logoutService.logout(userId, appId, reason);
        return Result.success(true);
    }

    /**
     * 全局登出：当前登录用户一键全退（SSO 会话 + 全部应用 token），广播档3事件.
     *
     * <p>应用 token（loginId="app:"+appId）无用户维度，拒绝调用；
     * 不受设备闸限制（gate path-pattern 为 {@code /api/auth/*&#47;logout}，本端点不匹配）。</p>
     *
     * <p>{@code @SaCheckLogin} 在 AuthFilter（enforce-login）之后确属二道校验，
     * 但作为端点级自保护契约保留：防止白名单误配或 enforce-login 关闭时
     * 这个清全量会话的端点静默裸奔（与 @SaCheckRole 隐含 login 校验同思路）。</p>
     */
    @SaCheckLogin
    @Override
    public IResult<Boolean> logout() {
        Object loginId = StpUtil.getLoginId();
        if (SsoTokenUtils.isAppLoginId(loginId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "应用 token 不支持全局登出");
        }
        logoutService.logout(StpUtil.getLoginIdAsLong(), null, "user-logout");
        return Result.success(true);
    }

    /**
     * 拼接 authorize 回跳查询串：每个值单独 URL 编码，scope/nonce/state 均用户可控，
     * 不编码可注入额外参数（如 scope=openid&amp;nonce=x）.
     */
    private String buildQuery(String appId, String redirectUri, String scope, String nonce, String state) {
        StringBuilder sb = new StringBuilder("appId=").append(encode(appId))
                .append("&redirectUri=").append(encode(redirectUri))
                .append("&scope=").append(encode(scope));
        if (nonce != null) {
            sb.append("&nonce=").append(encode(nonce));
        }
        if (state != null) {
            sb.append("&state=").append(encode(state));
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

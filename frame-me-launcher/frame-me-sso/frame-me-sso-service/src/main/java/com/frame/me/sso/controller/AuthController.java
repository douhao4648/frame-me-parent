package com.frame.me.sso.controller;

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
import com.frame.me.sso.service.AppService;
import com.frame.me.sso.service.AuthCodeService;
import com.frame.me.sso.service.LogoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * 授权端点：校验应用 → 未登录重定向登录页 → 已登录发 code 回调.
     */
    @Anonymous
    @Override
    public ResponseEntity<Void> authorize(@RequestParam String appId,
                                          @RequestParam String redirectUri,
                                          @RequestParam(required = false, defaultValue = "openid") String scope,
                                          @RequestParam(required = false) String nonce) {
        AppEntity app = appService.findByAppId(appId);
        if (app == null || !"ACTIVE".equals(app.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        if (!appService.isRedirectAllowed(app, redirectUri)) {
            return ResponseEntity.badRequest().build();
        }
        if (!StpUtil.isLogin()) {
            String target = "/sso-login.html?redirect=" +
                    URLEncoder.encode("/api/sso/authorize?" +
                            buildQuery(appId, redirectUri, scope, nonce), StandardCharsets.UTF_8);
            return ResponseEntity.status(302).location(URI.create(target)).build();
        }
        Long userId = StpUtil.getLoginIdAsLong();
        String code = authCodeService.issue(appId, userId, scope, redirectUri);
        String sep = redirectUri.contains("?") ? "&" : "?";
        return ResponseEntity.status(302).location(URI.create(redirectUri + sep + "code=" + code)).build();
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
                .body("{\"code\":200,\"msg\":\"请 POST /api/auth/login\"}");
    }

    /**
     * 换 token：code → sa-token 不透明 token.
     *
     * <p>用 {@link StpUtil#createLoginSession} 而非 {@link StpUtil#login}：本端点由下游服务端
     * POST 调用，无浏览器请求上下文，{@code createLoginSession} 不依赖上下文纯建会话返 token.
     * {@code is-concurrent: true} 保证与浏览器会话独立.</p>
     */
    @Anonymous
    @Override
    public IResult<TokenVO> token(@Valid @RequestBody TokenRequestDTO req) {
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
        // sa-token 不透明 token：带 app 维度（deviceType=appId）+ 独立 TTL，下游凭此调 /userinfo
        long timeout = properties.getToken().getAppTimeout().getSeconds();
        String token = StpUtil.createLoginSession(payload.userId,
                new SaLoginParameter()
                        .setDeviceType(payload.appId)
                        .setTimeout(timeout));
        TokenVO vo = new TokenVO();
        vo.setAccessToken(token);
        return Result.success(vo);
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

    private String buildQuery(String appId, String redirectUri, String scope, String nonce) {
        StringBuilder sb = new StringBuilder("appId=").append(appId)
                .append("&redirectUri=").append(redirectUri)
                .append("&scope=").append(scope);
        if (nonce != null) {
            sb.append("&nonce=").append(nonce);
        }
        return sb.toString();
    }
}

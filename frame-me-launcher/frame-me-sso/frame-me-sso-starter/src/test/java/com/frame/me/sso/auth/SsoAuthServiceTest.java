package com.frame.me.sso.auth;

import com.frame.me.auth.spi.IAuthService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.sso.api.IAuthApi;
import com.frame.me.sso.api.IUserApi;
import com.frame.me.sso.api.vo.TokenVO;
import com.frame.me.sso.api.vo.UserInfoVO;
import com.frame.me.sso.config.SsoClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SsoAuthService#loadUserByUpstreamToken} 单元测试：SSO token 回源 /userinfo 重建 User 的全分支.
 *
 * @author frame-me
 */
class SsoAuthServiceTest {

    private static final String APP_ID = "fm-audit-test";

    private final IAuthApi authApi = mock(IAuthApi.class);
    private final IUserApi userApi = mock(IUserApi.class);
    private final IAuthService authService = mock(IAuthService.class);
    private final SsoClientProperties ssoProps = new SsoClientProperties();
    private final SsoAuthService service = new SsoAuthService(authApi, userApi, ssoProps, authService);

    @BeforeEach
    void setUp() {
        ssoProps.setAppId(APP_ID);
    }

    private UserInfoVO stubUserInfo(String sub) {
        UserInfoVO info = new UserInfoVO();
        info.setSub(sub);
        info.setAccount("alice");
        info.setName("Alice");
        return info;
    }

    /**
     * 留存 token + /userinfo 成功 → 重建 User（快照缺失场景，用户无需重新登录）.
     */
    @Test
    void loadUserByUpstreamToken_tokenHit_rebuildsUser() {
        when(authService.getUpstreamToken(1001L, APP_ID)).thenReturn("sso-token-abc");
        when(userApi.userinfo("Bearer sso-token-abc"))
                .thenReturn(Result.success(stubUserInfo("1001")));

        User user = service.loadUserByUpstreamToken(1001L);

        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo(1001L);
        assertThat(user.getAccount()).isEqualTo("alice");
        assertThat(user.getNickname()).isEqualTo("Alice");
        assertThat(user.getStatus()).isEqualTo(User.STATUS_ENABLED);
    }

    /**
     * 无留存 token（session 过期/被踢）→ null（fail-closed，下游 401 重登）.
     */
    @Test
    void loadUserByUpstreamToken_noToken_returnsNull() {
        when(authService.getUpstreamToken(1001L, APP_ID)).thenReturn(null);

        assertThat(service.loadUserByUpstreamToken(1001L)).isNull();
        assertThat(service.loadUserByUpstreamToken(null)).isNull();
    }

    /**
     * /userinfo 401（token 被 SSO 侧作废，踢人场景）→ null，不放行.
     */
    @Test
    void loadUserByUpstreamToken_userinfoUnauthorized_returnsNull() {
        when(authService.getUpstreamToken(1001L, APP_ID)).thenReturn("sso-token-abc");
        when(userApi.userinfo("Bearer sso-token-abc")).thenReturn(Result.error(401, "未登录"));

        assertThat(service.loadUserByUpstreamToken(1001L)).isNull();
    }

    /**
     * /userinfo 远程调用异常 → null（按"无法重建"处理，不吞成 5xx 也不放行）.
     */
    @Test
    void loadUserByUpstreamToken_userinfoThrows_returnsNull() {
        when(authService.getUpstreamToken(1001L, APP_ID)).thenReturn("sso-token-abc");
        when(userApi.userinfo("Bearer sso-token-abc")).thenThrow(new RuntimeException("connection refused"));

        assertThat(service.loadUserByUpstreamToken(1001L)).isNull();
    }

    /**
     * /userinfo 返回用户与请求用户串号 → 拒绝（上游数据属信任边界）.
     */
    @Test
    void loadUserByUpstreamToken_userMismatch_returnsNull() {
        when(authService.getUpstreamToken(1001L, APP_ID)).thenReturn("sso-token-abc");
        when(userApi.userinfo("Bearer sso-token-abc")).thenReturn(Result.success(stubUserInfo("9999")));

        assertThat(service.loadUserByUpstreamToken(1001L)).isNull();
    }

    private TokenVO stubTokenVO(String token) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(token);
        return vo;
    }

    /**
     * 应用 token：首次换取并缓存，缓存期内不再调 SSO；invalidate 后强制重取.
     */
    @Test
    void getAppToken_cachesAndInvalidates() {
        when(authApi.token(any())).thenReturn(Result.success(stubTokenVO("app-token-1")));

        assertThat(service.getAppToken()).isEqualTo("app-token-1");
        assertThat(service.getAppToken()).isEqualTo("app-token-1");
        // 缓存命中，只换了一次
        verify(authApi, times(1)).token(any());

        // invalidate 后强制重取
        when(authApi.token(any())).thenReturn(Result.success(stubTokenVO("app-token-2")));
        service.invalidateAppToken();
        assertThat(service.getAppToken()).isEqualTo("app-token-2");
        verify(authApi, times(2)).token(any());
    }

    /**
     * 缓存 TTL 过期后自动重取（TTL 设为 0 即每次调用都重取）.
     */
    @Test
    void getAppToken_refetchesAfterTtlExpiry() {
        ssoProps.setAppTokenCacheTtl(Duration.ZERO);
        when(authApi.token(any())).thenReturn(Result.success(stubTokenVO("app-token-1")));

        service.getAppToken();
        service.getAppToken();
        verify(authApi, times(2)).token(any());
    }

    /**
     * 换取失败（appId/secret 错误或 SSO 拒绝）→ 4001 凭证错误，不写缓存.
     */
    @Test
    void getAppToken_fetchFailure_throwsBadCredential() {
        when(authApi.token(any())).thenReturn(Result.error(4001, "应用密钥错误"));

        assertThatThrownBy(() -> service.getAppToken())
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ResultCode.BAD_CREDENTIAL.getCode()));
        // 失败不缓存：下次调用重试而非返回毒缓存
        when(authApi.token(any())).thenReturn(Result.success(stubTokenVO("app-token-ok")));
        assertThat(service.getAppToken()).isEqualTo("app-token-ok");
    }

    /**
     * ssoLogin 正常路径：code 换 SSO token → /userinfo 取用户 → 建下游会话 + 留存上游 token.
     */
    @Test
    void ssoLogin_codeToUser_buildsLocalSession() {
        ssoProps.setAppSecret("secret");
        ssoProps.setRedirectUri("http://localhost/cb");
        when(authApi.token(any())).thenReturn(Result.success(stubTokenVO("sso-token-xyz")));
        when(userApi.userinfo("Bearer sso-token-xyz"))
                .thenReturn(Result.success(stubUserInfo("2002")));
        when(authService.loginByUser(any(User.class))).thenReturn("local-token");

        String localToken = service.ssoLogin("auth-code-123");

        assertThat(localToken).isEqualTo("local-token");
        verify(authService).storeUpstreamToken(2002L, APP_ID, "sso-token-xyz");
    }

    /**
     * ssoLogin 上游 sub 非数字 → 4001 凭证错误（而非 NumberFormatException 逸出成 500）.
     *
     * <p>ssoLogin 路径调 toUser 前不校验 sub 格式（区别于 loadUserByUpstreamToken 的
     * userId.equals(sub) 间接保证），上游返回非数字 sub 时应抛 4001 而非 500.</p>
     */
    @Test
    void ssoLogin_nonNumericSub_throwsBadCredential() {
        ssoProps.setAppSecret("secret");
        ssoProps.setRedirectUri("http://localhost/cb");
        when(authApi.token(any())).thenReturn(Result.success(stubTokenVO("sso-token-xyz")));
        when(userApi.userinfo("Bearer sso-token-xyz"))
                .thenReturn(Result.success(stubUserInfo("not-a-number")));

        assertThatThrownBy(() -> service.ssoLogin("auth-code-123"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ResultCode.BAD_CREDENTIAL.getCode()));
    }
}

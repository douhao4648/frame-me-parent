package com.frame.me.sso.auth;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.web.vo.TokenVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SsoAuthController#ssoLogin} 单元测试：token 对拆分契约.
 *
 * @author frame-me
 */
class SsoAuthControllerTest {

    private final SsoAuthService ssoAuthService = mock(SsoAuthService.class);
    private final SsoAuthController controller = new SsoAuthController(ssoAuthService);

    private static SsoLoginDTO dto(String code) {
        SsoLoginDTO dto = new SsoLoginDTO();
        dto.setCode(code);
        return dto;
    }

    /**
     * sa-token：单一不透明 token（无分号），accessToken 原样返回，refreshToken 为 null.
     */
    @Test
    void ssoLogin_opaqueToken_refreshTokenNull() {
        when(ssoAuthService.ssoLogin("code-1")).thenReturn("opaque-token");

        IResult<TokenVO> result = controller.ssoLogin(dto("code-1"));

        assertThat(result.getData().getAccessToken()).isEqualTo("opaque-token");
        assertThat(result.getData().getRefreshToken()).isNull();
    }

    /**
     * JWT：{@code "access;refresh"} token 对按分号拆分，与 JwtAuthController 契约对齐——
     * 整串塞 accessToken 会让客户端拿 {@code "access;refresh"} 当 Bearer token 用，解析失败全 401.
     */
    @Test
    void ssoLogin_tokenPair_splitsOnSemicolon() {
        when(ssoAuthService.ssoLogin("code-1")).thenReturn("access-abc;refresh-xyz");

        IResult<TokenVO> result = controller.ssoLogin(dto("code-1"));

        assertThat(result.getData().getAccessToken()).isEqualTo("access-abc");
        assertThat(result.getData().getRefreshToken()).isEqualTo("refresh-xyz");
    }
}

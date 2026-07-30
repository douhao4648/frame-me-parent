package com.frame.me.auth.jwt.web;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link JwtAuthController} 自定义 token-header 测试：
 * refresh 端点必须与 Resolver/logout 走同一个 {@code me.auth.jwt.token-header} 配置源.
 *
 * @author frame-me
 */
@WebMvcTest(JwtAuthController.class)
@Import(JwtAuthProperties.class)
@TestPropertySource(properties = "me.auth.jwt.token-header=X-Auth-Token")
class JwtAuthControllerCustomHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;

    @MockitoBean
    private AuthProperties authProperties;

    @Test
    void testRefreshReadsConfiguredHeader() throws Exception {
        when(authService.refresh("Bearer refreshToken")).thenReturn("newAccess;newRefresh");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Auth-Token", "Bearer refreshToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("newAccess"));

        verify(authService).refresh("Bearer refreshToken");
    }

    /**
     * 配置了自定义头名后，默认 Authorization 头不再被 refresh 读取（非 Cookie 模式拿到 null）.
     */
    @Test
    void testRefreshIgnoresAuthorizationWhenHeaderCustomized() throws Exception {
        when(authService.refresh(null)).thenReturn("newAccess;newRefresh");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer refreshToken"))
                .andExpect(status().isOk());

        verify(authService).refresh(null);
    }

    @SpringBootApplication
    static class TestApplication {
    }
}

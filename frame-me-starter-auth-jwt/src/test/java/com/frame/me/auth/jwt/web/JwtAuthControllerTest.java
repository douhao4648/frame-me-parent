package com.frame.me.auth.jwt.web;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.resolver.LoginUserArgumentResolver;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link JwtAuthController} Web 层单元测试.
 *
 * @author frame-me
 */
@WebMvcTest(JwtAuthController.class)
@Import({JwtAuthProperties.class, JwtAuthControllerTest.LoginUserResolverConfig.class})
class JwtAuthControllerTest {

    /**
     * 注册 {@link LoginUserArgumentResolver}，使 {@code @LoginUser} 参数能从 {@link AuthContext} 解析.
     */
    @Configuration(proxyBeanMethods = false)
    static class LoginUserResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new LoginUserArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;

    @MockitoBean
    private AuthProperties authProperties;

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void testLoginSuccess() throws Exception {
        when(authService.login("admin", "123456")).thenReturn("accessToken;refreshToken");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.data.refreshToken").value("refreshToken"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void testLoginValidationFailed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRefresh() throws Exception {
        when(authService.refresh("Bearer refreshToken")).thenReturn("newAccess;newRefresh");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer refreshToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("newAccess"))
                .andExpect(jsonPath("$.data.refreshToken").value("newRefresh"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer accessToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    /**
     * RFC 6750 §2.1：Bearer 前缀大小写不敏感。
     * logout 端点用小写 {@code bearer} 前缀应正确剥离并放行（旧实现会因 startsWith 大小写敏感返回 null）。
     */
    @Test
    void testLogoutWithLowercaseBearerPrefix() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "bearer accessToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void adminLogout_enabledCallsService() throws Exception {
        AuthProperties.Admin admin = new AuthProperties.Admin();
        admin.setLogoutEnabled(true);
        when(authProperties.getAdmin()).thenReturn(admin);

        mockMvc.perform(post("/api/auth/admin/logout/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(authService).logoutByUserId(123L);
    }

    @Test
    void testAdminLogoutByUserId_disabledReturnsNotFound() throws Exception {
        // 默认 me.auth.admin.logout.enabled=false，未启用时返回 404
        mockMvc.perform(post("/api/auth/admin/logout/123"))
                .andExpect(status().isNotFound());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}

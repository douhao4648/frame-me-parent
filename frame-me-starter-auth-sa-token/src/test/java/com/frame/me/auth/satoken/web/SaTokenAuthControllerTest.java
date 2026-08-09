package com.frame.me.auth.satoken.web;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SaTokenAuthController} Web 层单元测试.
 *
 * @author frame-me
 */
@WebMvcTest(SaTokenAuthController.class)
@Import(SaTokenAuthControllerTest.LoginUserResolverConfig.class)
class SaTokenAuthControllerTest {

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

    /**
     * 启用强制登出：调用 Service.
     */
    @Test
    void adminLogout_enabledCallsService() throws Exception {
        AuthProperties.Admin admin = new AuthProperties.Admin();
        admin.setLogoutEnabled(true);
        when(authProperties.getAdmin()).thenReturn(admin);

        mockMvc.perform(post("/api/auth/admin/123/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(authService).logoutByUserId(123L);
    }

    @Test
    void testAdminLogoutByUserId_disabledReturnsNotFound() throws Exception {
        // 默认 me.auth.admin.logout.enabled=false，未启用时返回 404
        mockMvc.perform(post("/api/auth/admin/123/logout"))
                .andExpect(status().isNotFound());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}

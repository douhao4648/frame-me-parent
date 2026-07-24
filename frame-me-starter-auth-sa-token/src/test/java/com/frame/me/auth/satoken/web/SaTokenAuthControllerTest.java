package com.frame.me.auth.satoken.web;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.spi.IAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
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
class SaTokenAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;

    @MockitoBean
    private AuthProperties authProperties;

    @Test
    void testAdminLogoutByUserId() throws Exception {
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

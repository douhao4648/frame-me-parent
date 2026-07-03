package com.frame.me.auth.jwt.web;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
@Import(JwtAuthProperties.class)
class JwtAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;

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

    @SpringBootApplication
    static class TestApplication {
    }
}

package com.frame.me.auth.jwt.web;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link JwtAuthController} Cookie 模式单元测试.
 *
 * @author frame-me
 */
@WebMvcTest(JwtAuthController.class)
@Import(JwtAuthProperties.class)
@TestPropertySource(properties = "me.auth.jwt.cookie-domain=.example.com")
class JwtAuthControllerCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;

    @Test
    void testLoginWritesCookieAndHidesRefreshToken() throws Exception {
        when(authService.login("admin", "123456")).thenReturn("accessToken;refreshToken");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("refreshToken=refreshToken")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Domain=.example.com")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Secure")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=604800")));
    }

    @Test
    void testRefreshReadsCookieAndWritesNewCookie() throws Exception {
        when(authService.refresh("cookieRefresh")).thenReturn("newAccess;newRefresh");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookieRefresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("newAccess"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("refreshToken=newRefresh")));

        verify(authService).refresh("cookieRefresh");
    }

    @Test
    void testRefreshHeaderTakesPrecedenceOverCookie() throws Exception {
        when(authService.refresh("Bearer headerRefresh")).thenReturn("newAccess;newRefresh");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer headerRefresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookieRefresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("newAccess"));

        verify(authService).refresh("Bearer headerRefresh");
        verify(authService, never()).refresh("cookieRefresh");
    }

    @Test
    void testLogoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer accessToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Domain=.example.com")));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}

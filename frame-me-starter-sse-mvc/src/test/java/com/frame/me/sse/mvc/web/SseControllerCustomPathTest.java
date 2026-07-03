package com.frame.me.sse.mvc.web;

import com.frame.me.sse.mvc.config.SseProperties;
import com.frame.me.sse.mvc.core.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SseController} 自定义路径集成测试.
 *
 * @author frame-me
 */
@WebMvcTest(SseController.class)
@Import(SseProperties.class)
@TestPropertySource(properties = "me.sse.path=/custom/sse")
class SseControllerCustomPathTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseEmitterManager emitterManager;

    @Test
    void shouldReturnSseEmitterForBroadcastWithCustomPath() throws Exception {
        mockMvc.perform(get("/custom/sse/subscribe/user:created"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
                    assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
                });
    }

    @Test
    void shouldReturnSseEmitterForTargetedWithCustomPath() throws Exception {
        mockMvc.perform(get("/custom/sse/subscribe?receiverId=user:123"))
                .andExpect(status().isOk());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}

package com.frame.me.sse.mvc.web;

import com.frame.me.sse.mvc.config.SseProperties;
import com.frame.me.sse.mvc.core.SseEmitterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SseController} 集成测试.
 *
 * @author frame-me
 */
class SseControllerIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SseProperties properties = new SseProperties();
        SseEmitterManager manager = new SseEmitterManager(properties);
        SseController controller = new SseController(manager, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnSseEmitterForBroadcast() throws Exception {
        MvcResult result = mockMvc.perform(get("/me/sse/subscribe/user:created"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
    }

    @Test
    void shouldReturnSseEmitterForTargeted() throws Exception {
        MvcResult result = mockMvc.perform(get("/me/sse/subscribe?receiverId=user:123"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
    }
}

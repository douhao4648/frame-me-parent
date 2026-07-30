package com.frame.me.adapter.result;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Response} 契约与序列化形态测试.
 *
 * @author frame-me
 */
class ResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * IResult 接口 getter 必须委托到旧系统字段，不能返回 null.
     */
    @Test
    void shouldDelegateInterfaceGettersToLegacyFields() {
        Map<String, Object> payload = Map.of("orderNo", "ORD123");
        Response<Map<String, Object>> response = new Response<>(200, "ok", payload, "rid-1");

        assertThat(response.getMsg()).isEqualTo("ok");
        assertThat(response.getData()).isEqualTo(payload);
        assertThat(response.getRid()).isEqualTo("rid-1");
        assertThat(response.getErr()).isNull();
        assertThat(response.isSuccess()).isTrue();
    }

    /**
     * 序列化报文只携带旧系统四字段（code/message/result/requestId），
     * 不混入规范字段名（msg/data/err/rid）与 success，避免旧系统收到多余 null 字段.
     */
    @Test
    void shouldSerializeOnlyLegacyFieldNames() {
        Response<String> response = new Response<>(200, "ok", "payload", "rid-1");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"code\":200")
                .contains("\"message\":\"ok\"")
                .contains("\"result\":\"payload\"")
                .contains("\"requestId\":\"rid-1\"")
                .doesNotContain("\"msg\":", "\"data\":", "\"err\":", "\"rid\":", "\"success\":");
    }
}

package com.frame.me.gateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GatewayGlobalExceptionHandler} 测试：错误响应与 base {@code IResult} 同构.
 *
 * @author frame-me
 */
class GatewayGlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayGlobalExceptionHandler handler = new GatewayGlobalExceptionHandler(objectMapper);

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/health"));
    }

    @Test
    void notFound_rendersIResultShape() {
        MockServerWebExchange exchange = exchange();
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND);

        handler.handle(exchange, notFound).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asInt()).isEqualTo(404);
        // 请求侧异常透传请求级 message（对齐 base 兜底分支语义）
        assertThat(body.get("msg").asString()).isEqualTo(notFound.getMessage());
        assertThat(body.get("rid").asString()).isNotBlank();
        // Boot 默认错误字段不得出现
        assertThat(body.has("timestamp")).isFalse();
        assertThat(body.has("path")).isFalse();
    }

    @Test
    void responseStatusException_usesReasonMessage() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.FORBIDDEN, "no route")).block();

        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asInt()).isEqualTo(403);
        assertThat(body.get("msg").asString()).contains("no route");
    }

    @Test
    void unknownException_maskedAs500() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new IllegalStateException("boom")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        // 对齐 base：未知异常对外掩码，真实 message 不进响应体
        assertThat(body.get("code").asInt()).isEqualTo(500);
        assertThat(body.get("msg").asString()).isEqualTo("系统错误");
    }

    @Test
    void writeError_sharedByAuthFilter() {
        MockServerWebExchange exchange = exchange();

        GatewayGlobalExceptionHandler.writeError(exchange, HttpStatus.UNAUTHORIZED, "missing credential",
                objectMapper).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asInt()).isEqualTo(401);
        assertThat(body.get("msg").asString()).isEqualTo("missing credential");
    }
}

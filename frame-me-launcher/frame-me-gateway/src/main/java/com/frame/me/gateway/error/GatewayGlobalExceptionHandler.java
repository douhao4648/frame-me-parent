package com.frame.me.gateway.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关统一错误响应：未捕获异常与未命中路由的 404 一律输出与 base {@code IResult} 同构的
 * {@code {code, msg, rid}} JSON（Boot 4 Jackson 默认省略 null 字段，与下游一致）.
 *
 * <p>语义对齐 base {@code GlobalExceptionHandler} 兜底分支：
 * {@link ErrorResponse} 请求侧异常（含 404 {@code NoResourceFoundException}）原样透传状态码与
 * 请求级 message；其余未知异常对外掩码为 500「系统错误」，真实堆栈只进服务端日志.</p>
 *
 * <p>必须 {@code @Order(-2)}：装配后虽顶掉 Boot 默认处理器（{@code @Order(-1)}），
 * 但 Spring 的 {@code ResponseStatusExceptionHandler} 仍在异常链中——不显式靠前，
 * 404 会被它先消费成空 body，本处理器不会执行.</p>
 *
 * @author frame-me
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * 写出 {@code IResult} 结构的错误 JSON，鉴权过滤器 401 与全局异常共用.
     */
    public static Mono<Void> writeError(ServerWebExchange exchange, HttpStatusCode status, String msg,
                                        ObjectMapper objectMapper) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("msg", msg);
        body.put("rid", exchange.getRequest().getId());
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (ex instanceof ErrorResponse errorResponse) {
            // 请求侧异常（404/405/415…）：客户端错误，透传状态码与请求级 message（对齐 base）
            return writeError(exchange, errorResponse.getStatusCode(), ex.getMessage(), objectMapper);
        }
        log.error("网关异常: {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), ex);
        return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "系统错误", objectMapper);
    }
}

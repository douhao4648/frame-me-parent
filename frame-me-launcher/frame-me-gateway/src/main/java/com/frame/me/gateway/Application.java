package com.frame.me.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 业务网关启动类.
 *
 * <p>职责：注册中心服务路由转发（{@code lb://}）、凭证驱动鉴权（user/app）、
 * 身份头契约（剥离外部身份头 + 认证后注入 {@code X-User-Id}）、优雅上下线。
 * 见 {@code docs/guides/gateway.md}.</p>
 *
 * @author frame-me
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

package com.frame.me.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 登录限流配置绑定（{@code me.auth.login-rate-limit.*}），仅供 {@link RedissonAutoConfiguration}
 * 注册 Redisson 分布式登录限流器使用.
 *
 * <p>与 {@code frame-me-starter-auth} 的 {@code AuthProperties.LoginRateLimit} 绑定同一前缀、
 * 默认值保持一致（multi-redis 不依赖 auth，无法直接复用其类型；两处配置类互不知晓，
 * Spring Boot 允许不同模块分别绑定同一前缀的子集）。修改默认值时需同步 auth 侧。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.auth.login-rate-limit")
public class LoginRateLimitProperties {

    /**
     * 时间窗口内最大登录尝试次数，默认 5.
     */
    private int maxAttempts = 5;

    /**
     * 速率限制窗口，默认 60 秒.
     */
    private Duration window = Duration.ofSeconds(60);
}

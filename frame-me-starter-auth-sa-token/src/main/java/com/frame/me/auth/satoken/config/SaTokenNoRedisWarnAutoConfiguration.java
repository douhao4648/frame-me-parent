package com.frame.me.auth.satoken.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * multi-redis 缺席告警配置.
 *
 * <p>{@code frame-me-starter-multi-redis} 不在 classpath 时，sa-token 退回内存 DAO，
 * 会话不跨实例共享——本配置仅在装配层用 {@code @ConditionalOnMissingClass} 表达一次条件，
 * 替代运行期字符串类名探针。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("com.frame.me.redis.util.RedisUtils")
@ConditionalOnProperty(prefix = "me.auth.sa-token", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SaTokenNoRedisWarnAutoConfiguration {

    /**
     * 提示会话存储退回内存模式.
     */
    @PostConstruct
    void warnIfNoRedis() {
        log.warn("未检测到 frame-me-starter-multi-redis，Sa-Token 会话将使用内存存储（单实例可用）。"
                + "多实例部署必须引入 frame-me-starter-multi-redis 以启用 Redis 会话后端。");
    }
}

package com.frame.me.base.limit;

/**
 * 登录速率限制器接口.
 *
 * <p>默认实现为 {@link InMemoryLoginRateLimiter}（单机内存计数），
 * classpath 存在 Redisson 时 {@code frame-me-starter-multi-redis} 提供分布式实现自动覆盖.</p>
 *
 * @author frame-me
 */
public interface LoginRateLimiter {

    /**
     * 尝试获取一次登录许可，超限抛异常.
     *
     * @param clientIp 客户端 IP
     */
    void acquire(String clientIp);
}

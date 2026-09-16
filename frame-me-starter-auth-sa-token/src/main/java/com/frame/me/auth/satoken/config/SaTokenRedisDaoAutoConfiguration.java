package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.dao.SaTokenDao;
import com.frame.me.auth.satoken.core.RedisSaTokenDao;
import com.frame.me.redis.util.RedisClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token Redis 会话后端自动配置.
 *
 * <p>显式引入 {@code frame-me-starter-multi-redis} 即激活（multi-redis 在本模块为
 * optional 依赖）：装配 {@link RedisSaTokenDao} 取代 sa-token 内存 DAO，
 * 会话跨实例共享，支撑踢人 / 封禁 / 在线会话等治理能力的集群语义。
 * 业务可声明自定义 {@link SaTokenDao} Bean 接管（本默认实现退避）。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
// multi-redis 为 optional 依赖：RedisClientRegistry 缺席时整个配置类在 ASM 元数据阶段跳过。
// 护栏：本条件必须保持在类级（类级 Class 字面量安全）；若改到方法级须换成 name 字符串形式，否则缺席场景会 NCDFE
@ConditionalOnClass(RedisClientRegistry.class)
@ConditionalOnBean(RedisClientRegistry.class)
// 与 SaTokenAuthAutoConfiguration 同走总开关：me.auth.enabled=false 或 me.auth.sa-token.enabled=false 时本配置一并退避
@ConditionalOnProperty(prefix = "me.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.sa-token", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.sa-token.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SaTokenAuthProperties.class)
@AutoConfigureAfter(name = "com.frame.me.redis.config.RedisAutoConfiguration")
public class SaTokenRedisDaoAutoConfiguration {

    /**
     * Sa-Token 会话的 Redis 存储后端.
     */
    @Bean
    @ConditionalOnMissingBean(SaTokenDao.class)
    public SaTokenDao redisSaTokenDao(SaTokenAuthProperties properties, RedisClientRegistry redisClients) {
        log.info("RedisSaTokenDao initialized: clientName={}", properties.getRedis().getClientName());
        return new RedisSaTokenDao(redisClients.getClient(properties.getRedis().getClientName()));
    }
}

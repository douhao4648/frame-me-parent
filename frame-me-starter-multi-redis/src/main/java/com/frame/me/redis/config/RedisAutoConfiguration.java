package com.frame.me.redis.config;

import com.frame.me.redis.util.RedisClient;
import com.frame.me.redis.util.RedisClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

/**
 * Redis 自动配置.
 *
 * <p>启用后注册当前容器独立的 {@link RedisClientRegistry}。
 * 默认实例来自 {@code spring.data.redis.*}，额外实例通过 {@code me.redis.clients.*} 配置。</p>
 *
 * <p>额外实例的 {@link LettuceConnectionFactory} 由本配置类手动创建、不注册为 Bean，
 * 故其生命周期由本类管理：实现 {@link DisposableBean}，容器关闭时统一销毁，避免 TCP 连接与
 * NIO 资源泄漏（默认实例的工厂由 Boot 管理，不在本类销毁范围）。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "me.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration implements DisposableBean {

    private static final String DEFAULT_CLIENT = "default";

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisTemplate<Object, Object> redisTemplate;

    private final RedisProperties redisProperties;

    /**
     * 额外实例的连接工厂：由本类创建、由本类销毁.
     */
    private final List<LettuceConnectionFactory> extraConnectionFactories = new ArrayList<>();

    public RedisAutoConfiguration(StringRedisTemplate stringRedisTemplate,
                                  RedisTemplate<Object, Object> redisTemplate,
                                  RedisProperties redisProperties) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties");
    }

    @Bean
    @ConditionalOnMissingBean(RedisClientRegistry.class)
    public RedisClientRegistry redisClientRegistry() {
        Map<String, RedisClient> clients = new HashMap<>();

        clients.put(DEFAULT_CLIENT, new RedisClient(stringRedisTemplate, redisTemplate));

        redisProperties.getClients().forEach((name, config) -> {
            LettuceConnectionFactory connectionFactory = buildConnectionFactory(config);
            extraConnectionFactories.add(connectionFactory);

            StringRedisTemplate extraStringTemplate = new StringRedisTemplate(connectionFactory);
            extraStringTemplate.afterPropertiesSet();
            RedisTemplate<Object, Object> extraTemplate = new RedisTemplate<>();
            extraTemplate.setConnectionFactory(connectionFactory);
            // ponytail: 额外实例用默认 JdkSerializationRedisSerializer，与 Boot 自动配置的默认实例一致
            // （默认实例也用 JdkSerialization，跨实例兼容）。如业务方为默认实例配置了自定义序列化器，
            // 应通过 RedisConfig @Bean 同步配置额外实例，或使用 RedisClient 的 String 通道.
            extraTemplate.afterPropertiesSet();

            clients.put(name, new RedisClient(extraStringTemplate, extraTemplate));
        });

        RedisClientRegistry registry = new RedisClientRegistry(DEFAULT_CLIENT, clients);
        log.info("Redis initialize : {}", registry.clientNames());
        return registry;
    }

    @Override
    public void destroy() {
        for (LettuceConnectionFactory factory : extraConnectionFactories) {
            try {
                factory.destroy();
            } catch (Exception e) {
                log.warn("销毁额外 Redis 连接工厂失败", e);
            }
        }
        extraConnectionFactories.clear();
    }

    /**
     * 按部署模式构建额外实例的连接工厂.
     *
     * <p>支持 {@code STANDALONE} / {@code CLUSTER} / {@code SENTINEL} 三种模式。</p>
     */
    private LettuceConnectionFactory buildConnectionFactory(RedisProperties.ClientConfig config) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder().build();
        RedisConfiguration redisConfiguration = switch (config.getMode()) {
            case CLUSTER -> {
                RedisClusterConfiguration cluster = new RedisClusterConfiguration(config.getNodes());
                applyAuth(config, cluster::setUsername, cluster::setPassword);
                yield cluster;
            }
            case SENTINEL -> {
                RedisSentinelConfiguration sentinel =
                        new RedisSentinelConfiguration(config.getSentinelMaster(), new HashSet<>(config.getNodes()));
                sentinel.setDatabase(config.getDatabase());
                applyAuth(config, sentinel::setUsername, sentinel::setPassword);
                yield sentinel;
            }
            case STANDALONE -> {
                RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
                standalone.setHostName(config.getHost());
                standalone.setPort(config.getPort());
                standalone.setDatabase(config.getDatabase());
                applyAuth(config, standalone::setUsername, standalone::setPassword);
                yield standalone;
            }
            default -> throw new IllegalArgumentException("不支持的 Redis 部署模式: " + config.getMode());
        };

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    /**
     * 统一设置用户名/密码（非空时）.
     */
    private void applyAuth(RedisProperties.ClientConfig config,
                           java.util.function.Consumer<String> usernameSetter,
                           java.util.function.Consumer<RedisPassword> passwordSetter) {
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            usernameSetter.accept(config.getUsername());
        }
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            passwordSetter.accept(RedisPassword.of(config.getPassword()));
        }
    }
}

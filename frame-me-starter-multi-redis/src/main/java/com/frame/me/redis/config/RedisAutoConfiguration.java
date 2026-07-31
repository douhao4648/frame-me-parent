package com.frame.me.redis.config;

import com.frame.me.redis.util.RedisUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Redis 自动配置.
 *
 * <p>启用后注册 {@link RedisUtils} 所需的 RedisTemplate 引用。
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

    @Autowired(required = false)
    public RedisAutoConfiguration(StringRedisTemplate stringRedisTemplate,
                                  RedisTemplate<Object, Object> redisTemplate,
                                  RedisProperties redisProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    @PostConstruct
    public void init() {
        Map<String, StringRedisTemplate> stringTemplates = new HashMap<>();
        Map<String, RedisTemplate<Object, Object>> templates = new HashMap<>();

        if (stringRedisTemplate != null) {
            stringTemplates.put(DEFAULT_CLIENT, stringRedisTemplate);
        }
        if (redisTemplate != null) {
            templates.put(DEFAULT_CLIENT, redisTemplate);
        }

        redisProperties.getClients().forEach((name, config) -> {
            LettuceConnectionFactory connectionFactory = buildConnectionFactory(config);
            extraConnectionFactories.add(connectionFactory);

            StringRedisTemplate extraStringTemplate = new StringRedisTemplate(connectionFactory);
            extraStringTemplate.afterPropertiesSet();
            RedisTemplate<Object, Object> extraTemplate = new RedisTemplate<>();
            extraTemplate.setConnectionFactory(connectionFactory);
            // ponytail: 额外实例用默认 JdkSerializationRedisSerializer，与 Boot 自动配置的默认实例一致
            // （默认实例也用 JdkSerialization，跨实例兼容）。如业务方为默认实例配置了自定义序列化器，
            // 应通过 RedisConfig @Bean 同步配置额外实例，或改用 RedisUtils.str() 通道（StringRedisSerializer）.
            extraTemplate.afterPropertiesSet();

            stringTemplates.put(name, extraStringTemplate);
            templates.put(name, extraTemplate);
        });

        RedisUtils.init(DEFAULT_CLIENT, stringTemplates, templates);
        log.info("Redis initialize : {}", stringTemplates.keySet());
    }

    @Override
    public void destroy() {
        for (LettuceConnectionFactory factory : extraConnectionFactories) {
            try {
                factory.destroy();
            } catch (Exception e) {
                log.warn("销毁额外 Redis 连接工厂失败: {}", e.getMessage());
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

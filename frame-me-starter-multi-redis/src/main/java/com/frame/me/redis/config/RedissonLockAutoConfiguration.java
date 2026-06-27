package com.frame.me.redis.config;

import com.frame.me.redis.util.RedissonLock;
import com.frame.me.redis.util.RedissonLimiter;
import com.frame.me.redis.util.RedissonSync;
import com.frame.me.redis.util.RedissonTopic;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Redisson 自动配置.
 *
 * <p>仅当 classpath 存在 Redisson（{@code org.redisson.api.RedissonClient}）时才装配，
 * 创建 {@link RedissonClient} 并注入本项目封装的 Redisson 工具类。</p>
 *
 * <p>当前已集成的 Redisson 能力：</p>
 * <ul>
 *   <li>分布式锁：{@link RedissonLock} 提供可重入锁与看门狗续期；</li>
 *   <li>同步原语：{@link RedissonSync} 提供读写锁、公平锁、联锁、信号量、倒计时门闩、
 *       可过期信号量（红锁 {@code RedissonRedLock} 已随 Redisson 4.x 弃用）；</li>
 *   <li>消息能力：{@link RedissonTopic} 提供 Topic、PatternTopic、ReliableTopic、Stream；</li>
 *   <li>限流：{@link RedissonLimiter} 提供基于令牌桶的 {@code RRateLimiter}。</li>
 * </ul>
 *
 * <p>Redisson 还原生支持以下高阶能力，可按需通过 {@link RedissonClient} 直接获取或进一步封装：</p>
 * <ul>
 *   <li>分布式对象：{@code RMap}、{@code RMapCache}、{@code RLocalCachedMap}、{@code RSet}、
 *       {@code RSortedSet}、{@code RScoredSortedSet}、{@code RBucket}、{@code RAtomicLong} 等；</li>
 *   <li>分布式集合：{@code RList}、{@code RQueue}、{@code RBlockingQueue}、{@code RDelayedQueue}；</li>
 *   <li>任务调度：{@code RExecutorService}、{@code RScheduledExecutorService}；</li>
 *   <li>RPC 与活对象：{@code RRemoteService}、{@code RLiveObjectService}；</li>
 *   <li>事务与脚本：{@code RTransaction}、{@code RScript}；</li>
 *   <li>Spring Cache：可通过 {@code RedissonCacheManager} 将 {@code RMapCache} 作为缓存后端。</li>
 * </ul>
 *
 * <p>配置优先级：配了 {@code spring.data.redis.redisson.config}（与 redisson-spring-boot-starter 对齐）时用
 * Redisson 原生 YAML（支持全部 5 种模式）；否则自动复用 {@code spring.data.redis.*}（配 {@code cluster} 走集群、
 * 配 {@code sentinel} 走哨兵，否则单机）。物理连接独立于 data-redis 的 Lettuce 连接。未引入 Redisson 时，
 * 分布式锁回退为 {@link com.frame.me.redis.util.RedisClient#tryLock} 的 {@code SET NX} 实现。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
@ConditionalOnProperty(prefix = "frame.me.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonLockAutoConfiguration {

    private RedissonClient redissonClient;

    @Bean(destroyMethod = "")
    public RedissonClient meRedissonClient(DataRedisProperties dataRedisProperties,
                                           RedissonProperties redissonProperties,
                                           ResourceLoader resourceLoader) throws IOException {
        Config config;
        String location = redissonProperties.getConfig();
        if (StringUtils.hasText(location)) {
            Resource resource = resourceLoader.getResource(location);
            try (InputStream in = resource.getInputStream()) {
                config = Config.fromYAML(in);
            }
            log.info("Redisson lock initialize from config : {}", location);
        } else {
            config = buildConfig(dataRedisProperties);
        }

        redissonClient = Redisson.create(config);
        RedissonLock.init(redissonClient);
        RedissonSync.init(redissonClient);
        RedissonTopic.init(redissonClient);
        RedissonLimiter.init(redissonClient);
        return redissonClient;
    }

    /**
     * 按 {@code spring.data.redis.*} 构建 Redisson 配置（single/cluster/sentinel）.
     */
    private Config buildConfig(DataRedisProperties properties) {
        String scheme = properties.getSsl().isEnabled() ? "rediss://" : "redis://";
        Config config = new Config();

        DataRedisProperties.Cluster cluster = properties.getCluster();
        DataRedisProperties.Sentinel sentinel = properties.getSentinel();

        if (cluster != null && cluster.getNodes() != null && !cluster.getNodes().isEmpty()) {
            config.useClusterServers()
                    .addNodeAddress(toAddresses(scheme, cluster.getNodes()));
            applyAuth(config, properties.getUsername(), properties.getPassword());
            log.info("Redisson lock initialize (cluster) : {}", cluster.getNodes());
        } else if (sentinel != null && sentinel.getMaster() != null && !sentinel.getMaster().isEmpty()) {
            assert sentinel.getNodes() != null;
            config.useSentinelServers()
                    .setMasterName(sentinel.getMaster())
                    .addSentinelAddress(toAddresses(scheme, sentinel.getNodes()))
                    .setDatabase(properties.getDatabase());
            applyAuth(config, properties.getUsername(), properties.getPassword());
            log.info("Redisson lock initialize (sentinel) : master={}, nodes={}", sentinel.getMaster(), sentinel.getNodes());
        } else {
            String host = properties.getHost();
            int port = properties.getPort() > 0 ? properties.getPort() : 6379;
            config.useSingleServer()
                    .setAddress(scheme + host + ":" + port)
                    .setDatabase(properties.getDatabase());
            applyAuth(config, properties.getUsername(), properties.getPassword());
            log.info("Redisson lock initialize (single) : {}:{} (db={})", host, port, properties.getDatabase());
        }
        return config;
    }

    /**
     * 给 {@code host:port} 列表加上 {@code redis://} / {@code rediss://} 前缀.
     */
    private String[] toAddresses(String scheme, List<String> nodes) {
        return nodes.stream().map(node -> scheme + node).toArray(String[]::new);
    }

    /**
     * 统一设置用户名/密码（非空时）.
     *
     * <p>Redisson 4.x 中 {@code BaseConfig#setUsername(String)} / {@code BaseConfig#setPassword(String)}
     * 已被弃用，改为在顶层 {@link Config} 对象上设置。</p>
     */
    private void applyAuth(Config config, String username, String password) {
        if (username != null && !username.isEmpty()) {
            config.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
    }

    @PreDestroy
    public void destroy() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
        }
    }
}

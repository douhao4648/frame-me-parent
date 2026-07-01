package com.frame.me.mybatis.flex.config;

import com.frame.me.base.util.SnowflakeUtils;
import com.mybatisflex.core.keygen.KeyGeneratorFactory;
import com.mybatisflex.core.keygen.KeyGenerators;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * MyBatis-Flex 插件配置.
 *
 * <p>当类路径存在 BaseMapper 时自动启用，注册全局配置以及可选的自定义雪花算法 ID 生成器适配器，
 * 同时修复 MyBatis-Flex 内部配置类在 BeanPostProcessor 阶段被提前实例化而产生的 WARN。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.mybatisflex.core.BaseMapper")
@Import(MybatisFlexInfrastructureRoleFixer.class)
public class MybatisFlexConfiguration {

    /**
     * 雪花主键生成器适配.
     *
     * <p>当类路径存在 base 的 {@link SnowflakeUtils} 时，覆盖 MyBatis-Flex 内置的
     * {@link KeyGenerators#snowFlakeId} 生成器，改为委托 {@code SnowflakeUtils.nextId()}，
     * 使 flex 主键与 base 使用同一套雪花实例（含 {@code me.snowflake.*} 配置）。
     * base 不存在时本配置不加载，flex 沿用自带雪花。</p>
     */
    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SnowflakeUtils.class)
    @ConditionalOnProperty(prefix = "me.snowflake", name = "worker-id")
    static class SnowflakeKeyGeneratorConfiguration {

        @PostConstruct
        public void registerSnowflakeKeyGenerator() {
            KeyGeneratorFactory.register(KeyGenerators.snowFlakeId,
                    (entity, keyColumn) -> SnowflakeUtils.nextId());
            log.info("MyBatis-Flex snowFlakeId generator delegated to base SnowflakeUtils");
        }
    }

}

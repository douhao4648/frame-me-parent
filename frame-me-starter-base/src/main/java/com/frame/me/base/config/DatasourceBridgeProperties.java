package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据源桥接配置属性.
 *
 * <p>绑定前缀 {@code me.mybatis.datasource-bridge}。控制「{@code spring.datasource} 自动注册为
 * master 数据源」的桥接行为，由 {@code frame-me-starter-mybatis-flex}（EnvironmentPostProcessor）
 * 与 {@code frame-me-starter-dynamic-ds}（@ConditionalOnProperty）共用。</p>
 *
 * <p>注意：两个消费者都早于属性绑定阶段（前者在 EnvironmentPostProcessor 直接读 Environment，
 * 后者走条件注解的原始 key 匹配），本类不参与它们的判定逻辑，仅提供类型化绑定与 IDE 配置提示，
 * 供业务方需要编程读取该开关时注入使用。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.mybatis.datasource-bridge")
public class DatasourceBridgeProperties {

    /**
     * 是否启用 spring.datasource → master 数据源桥接，默认 true.
     */
    private boolean enabled = true;
}

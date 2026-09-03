package com.frame.me.gateway.config;

import com.frame.me.gateway.filter.GatewayAccessLogFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关访问日志装配：{@code me.gateway.access-log.enabled=true} 时才装配过滤器（默认关闭，零开销）.
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayAccessLogProperties.class)
public class GatewayAccessLogConfiguration {

    /**
     * 访问日志过滤器（WebFilter，最外层，覆盖未命中路由的 404）.
     */
    @Bean
    @ConditionalOnProperty(prefix = "me.gateway.access-log", name = "enabled", havingValue = "true")
    public GatewayAccessLogFilter gatewayAccessLogFilter(GatewayAccessLogProperties properties) {
        log.info("网关访问日志已启用（max-length={}）", properties.getMaxLength());
        return new GatewayAccessLogFilter(properties);
    }
}

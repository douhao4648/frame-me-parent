package com.frame.me.audit;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 审计中心启动类.
 *
 * <p>{@code @Import(AuditLogEventConfiguration.class)} 注册 {@code AuditLogEventType}，
 * 使 {@code EventBridgeListener} 订阅 {@code audit:log} 通道，把跨服务广播的审计事件
 * 还原为本地 {@code AuditLogEvent} 供 {@code AuditLogPersistenceHandler} 持久化。</p>
 *
 * <p>踢人事件（{@code sso:user-logout}）类型由 {@code frame-me-sso-starter} 的
 * {@code SsoClientAutoConfiguration} 已 {@code @Import(UserLogoutEventConfiguration.class)}
 * 注册，本类无需重复。</p>
 *
 * @author frame-me
 */
@EnableMethodCache(basePackages = "com.frame.me.audit")
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

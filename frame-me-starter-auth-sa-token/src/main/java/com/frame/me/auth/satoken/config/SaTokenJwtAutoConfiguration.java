package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.stp.StpLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Sa-Token JWT Token 模式自动配置.
 *
 * <p>默认关闭；开启后把 sa-token 的 {@link StpLogic} 替换为
 * {@code cn.dev33.satoken.jwt.StpLogicJwtForSimple}，使 {@code StpUtil.login(...)} 颁发的 Token
 * 变为 JWT 格式，便于网关节点独立验签。</p>
 *
 * <p>采用 Simple 模式：Token 格式为 JWT，会话数据仍存 Redis，因此踢人 / 在线会话 /
 * 强制登出等治理能力继续生效。用户需要在业务工程显式引入 {@code sa-token-jwt} 依赖，
 * 并在 {@code sa-token.jwt-secret-key} 配置签名密钥。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.auth.sa-token", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.sa-token.jwt", name = "enabled", havingValue = "true")
@ConditionalOnClass(cn.dev33.satoken.jwt.StpLogicJwtForSimple.class)
public class SaTokenJwtAutoConfiguration {

    /**
     * 注册 JWT 版 StpLogic，覆盖默认不透明 Token 实现.
     */
    @Bean
    @Primary
    public StpLogic saTokenJwtStpLogic() {
        log.info("Sa-Token JWT Token mode enabled (StpLogicJwtForSimple)");
        return new cn.dev33.satoken.jwt.StpLogicJwtForSimple();
    }
}

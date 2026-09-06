package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.stp.StpLogic;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;

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
 * <p>sa-token 原生 starter 不对 {@code jwt-secret-key} 做强制校验，缺失时会用空密钥签发 JWT，
 * 网关独立验签时可能因密钥缺失而通过非法 token。本类在启动期 fail-fast，
 * 与 frame-me-starter-auth-jwt 的 {@code JwtTokenServiceImpl.validateSecret()} 对齐。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.sa-token", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.sa-token.jwt", name = "enabled", havingValue = "true")
@ConditionalOnClass(cn.dev33.satoken.jwt.StpLogicJwtForSimple.class)
@RequiredArgsConstructor
public class SaTokenJwtAutoConfiguration {

    /**
     * HS256 签名密钥最低字节数（256 位）.
     */
    private static final int MIN_SECRET_BYTES = 32;

    private final Environment environment;

    /**
     * 启动期 fail-fast：{@code me.auth.sa-token.jwt.enabled=true} 时，
     * {@code sa-token.jwt-secret-key} 必须已配置且不少于 32 字节（256 位），否则阻止启动.
     *
     * <p>密钥缺失 / 过短时 sa-token 仍会正常签发 JWT（hutool 不对密钥强度校验），
     * 但签出的 token 可被伪造，网关独立验签形同虚设。把问题从「首次签发不安全 token」
     * 提前到启动期暴露。</p>
     */
    @PostConstruct
    void validateJwtSecret() {
        String secret = environment.getProperty("sa-token.jwt-secret-key");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "me.auth.sa-token.jwt.enabled=true 但 sa-token.jwt-secret-key 未配置："
                            + "JWT 签名密钥为必填项，请配置不少于 32 字节（256 位）的随机密钥");
        }
        int byteLen = secret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "sa-token.jwt-secret-key 长度不足：HS256 要求不少于 " + MIN_SECRET_BYTES
                            + " 字节（256 位），当前仅 " + byteLen + " 字节，请更换更强的密钥");
        }
    }

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

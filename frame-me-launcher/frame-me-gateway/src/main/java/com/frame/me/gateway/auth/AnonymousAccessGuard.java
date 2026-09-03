package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 匿名访问守卫：{@code allow-anonymous=true}（无凭证匿名放行）仅在显式激活
 * {@code internal} profile 时合法，否则启动失败.
 *
 * <p>fail-closed 方向：高危的是"匿名放行"，因此它必须依附于内网拓扑的显式声明——
 * 而不是反过来由公网实例自觉声明 {@code public}。两个方向的配置遗忘都落在安全侧：
 * 公网部署忘记加拓扑 profile → 无 internal → 匿名配置直接拒绝启动；
 * 内网部署忘记加 internal → 同样拒绝，迫使显式声明。</p>
 *
 * <p>守卫默认开启，可显式置 {@code me.gateway.auth.anonymous-guard-enabled=false} 关闭
 * （放弃防呆保护，仅用于明确知晓风险的场景）。</p>
 *
 * @author frame-me
 */
@Slf4j
public class AnonymousAccessGuard {

    public AnonymousAccessGuard(Environment environment, GatewayAuthProperties properties) {
        if (!properties.isAnonymousGuardEnabled()) {
            log.warn("匿名放行守卫已手动关闭（anonymous-guard-enabled=false），"
                    + "allow-anonymous=true 不再校验 internal profile——请确认知晓风险");
            return;
        }
        if (!properties.isAllowAnonymous()) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("internal"))) {
            throw new IllegalStateException(
                    "allow-anonymous=true 仅在 internal profile 下合法——匿名放行是内网拓扑的显式声明，"
                            + "未激活 internal（含忘记配置拓扑 profile）一律拒绝启动");
        }
        log.info("internal profile：无凭证请求匿名放行已确认开启");
    }
}

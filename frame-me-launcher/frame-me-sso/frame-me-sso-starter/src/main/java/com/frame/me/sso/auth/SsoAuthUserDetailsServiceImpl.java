package com.frame.me.sso.auth;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.user.User;
import org.springframework.context.annotation.Lazy;

/**
 * SSO RP 用户详情服务（无本地用户表的下游通用）.
 *
 * <p>由 {@code SsoAuthAutoConfiguration} 在下游未自定义 {@link IAuthUserDetailsService}
 * 时兜底装配（{@code @ConditionalOnMissingBean}）：RP 下游引本 starter 即开箱获得
 * "快照 miss 回源重建"能力；有本地用户表的下游实现自己的接口，本类自动退让。</p>
 *
 * <p>用户身份来自 SSO {@code /userinfo}：登录时 {@link SsoAuthService#ssoLogin} 建本地
 * 会话并写用户快照，后续读取缓存命中；{@link #loadUserById} 仅在快照缺失时被调，
 * 委托 {@link SsoAuthService#loadUserByUpstreamToken} 用留存的 SSO token 回源重建。
 * 失败两分语义：暂时无法确认（网络故障等）返回 {@code null}；SSO 明确判定失效
 * （被踢/禁用/串号）抛 {@code UpstreamUserInvalidException}，JWT 侧据此拒绝快照重建，
 * 保证"数据库已禁用 = 会话失效"。{@link #loadUserByAccount} 返回 {@code null}
 * （RP 无账号密码登录入口），不覆盖 {@code matches}（用不到 BCrypt）。</p>
 *
 * @author frame-me
 */
public class SsoAuthUserDetailsServiceImpl implements IAuthUserDetailsService {

    private final SsoAuthService ssoAuthService;

    /**
     * {@code @Lazy} 打破构造环：认证实现（{@code SaTokenAuthService}）构造依赖本接口，
     * 本类经 {@link SsoAuthService} 又依赖 {@code IAuthService}；延迟注入后首次调用时才解析.
     */
    public SsoAuthUserDetailsServiceImpl(@Lazy SsoAuthService ssoAuthService) {
        this.ssoAuthService = ssoAuthService;
    }

    @Override
    public User loadUserByAccount(String account) {
        // RP 场景无账号密码登录入口，不会被调用
        return null;
    }

    @Override
    public User loadUserById(Long id) {
        return ssoAuthService.loadUserByUpstreamToken(id);
    }
}

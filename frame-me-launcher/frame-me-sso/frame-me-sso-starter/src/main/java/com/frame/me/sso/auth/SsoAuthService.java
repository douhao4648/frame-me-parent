package com.frame.me.sso.auth;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.sso.api.IAuthApi;
import com.frame.me.sso.api.IUserApi;
import com.frame.me.sso.api.dto.TokenRequestDTO;
import com.frame.me.sso.api.vo.TokenVO;
import com.frame.me.sso.api.vo.UserInfoVO;
import com.frame.me.sso.config.SsoClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SSO 登录编排服务（RP 场景：code 换 token → 取用户 → 建下游本地会话）.
 *
 * <p>流程：
 * <ol>
 *   <li>用 SSO 授权码调 {@link IAuthApi#token} 换取 SSO sa-token</li>
 *   <li>用 SSO sa-token 调 {@link IUserApi#userinfo} 取用户信息</li>
 *   <li>{@link UserInfoVO} → {@link User}，调 {@link IAuthService#loginByUser}
 *       建下游自己的会话（sa-token 实现内部 {@code cacheUser} 存快照）</li>
 *   <li>{@link IAuthService#storeUpstreamToken} 留存 SSO token（随本地会话同生共死），
 *       下游后续可用 {@code getUpstreamToken} 取出回源调 SSO 接口</li>
 * </ol>
 * 下游不直接用 SSO sa-token 做日常鉴权，只用于"调 /userinfo 取用户"，与 sso.md 的
 * RP session 模式一致。</p>
 *
 * <p><b>认证实现通用</b>：{@link IAuthService#loginByUser} 由下游认证实现覆盖——
 * {@code SaTokenAuthService} 调 {@code StpLogic.login} 建会话（默认 {@code login} 账号体系），
 * {@code JwtTokenServiceImpl} 调 {@code buildTokenPair} 建 token 对。sa-token/JWT 两套下游均可用
 * sso-login 端点。</p>
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoAuthService {

    private final IAuthApi authApi;
    private final IUserApi userApi;
    private final SsoClientProperties ssoProps;
    private final IAuthService authService;

    /**
     * 应用 token 缓存（单条目：一个服务只持有自己 appId 的 token，无需 map 维度）.
     * volatile 保证缓存读路径（无锁）的可见性.
     */
    private volatile CachedAppToken appToken;
    private final Object appTokenLock = new Object();

    /**
     * SSO 授权码换下游本地会话 token.
     *
     * @param code SSO 授权码
     * @return 下游本地 token
     */
    public String ssoLogin(String code) {
        // 1. code 换 SSO sa-token
        TokenRequestDTO req = new TokenRequestDTO();
        req.setGrantType("authorization_code");
        req.setCode(code);
        req.setAppId(ssoProps.getAppId());
        req.setAppSecret(ssoProps.getAppSecret());
        // redirectUri 从配置读,与 authorize 跳转时用的地址一致（SSO 换 token 时会比对）
        req.setRedirectUri(ssoProps.getRedirectUri());
        IResult<TokenVO> tokenRes = authApi.token(req);
        if (tokenRes == null || !ResultCode.SUCCESS.getCode().equals(tokenRes.getCode())
                || tokenRes.getData() == null) {
            String msg = tokenRes == null ? "SSO 响应为空" : tokenRes.getMsg();
            // 授权码是短期凭证，日志只打前 8 位（打全码泄露进日志可被重放）
            log.warn("SSO 换 token 失败: code={}***, msg={}",
                    code == null ? null : code.substring(0, Math.min(8, code.length())), msg);
            // 凭证错误（4001）：RP 换会话流程的凭证有问题，前端留登录页显示错误，避免与"会话缺失"401 混淆导致循环重定向
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "SSO 授权码无效: " + msg);
        }
        String ssoToken = tokenRes.getData().getAccessToken();

        // 2. SSO token 换用户信息
        IResult<UserInfoVO> userRes = userApi.userinfo("Bearer " + ssoToken);
        if (userRes == null || !ResultCode.SUCCESS.getCode().equals(userRes.getCode())
                || userRes.getData() == null) {
            String msg = userRes == null ? "SSO 响应为空" : userRes.getMsg();
            log.warn("SSO 获取用户信息失败: msg={}", msg);
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "获取用户信息失败: " + msg);
        }
        UserInfoVO info = userRes.getData();

        // 3. UserInfoVO → User，建下游本地会话
        User user = toUser(info);
        String localToken = authService.loginByUser(user);
        // 4. 留存 SSO token（按 appId 隔离、随本地会话同生共死），供下游回源调 SSO 接口
        //    （如重新拉 /userinfo）；无留存需求的认证实现该调用为 no-op
        authService.storeUpstreamToken(user.getId(), ssoProps.getAppId(), ssoToken);
        return localToken;
    }

    /**
     * 用留存的上游 token 回源 {@code /userinfo} 重建 User（RP 下游本地会话快照缺失时调用）.
     *
     * <p>供下游 {@code IAuthUserDetailsService#loadUserById} 在缓存 miss 时委托调用：
     * 取 {@link IAuthService#getUpstreamToken} 留存的 SSO token 调 {@code /userinfo}。
     * 取不到（session 过期/被踢，token 已随会话销毁）或回源失败均返回 {@code null}——
     * fail-closed，下游走 401 重新 SSO 登录；SSO 侧踢人同时作废 token，
     * 即便取出残值 {@code /userinfo} 也 401，踢人语义不受破坏。</p>
     *
     * @param userId 用户 ID
     * @return 重建的 User，无法重建时返回 {@code null}
     */
    public User loadUserByUpstreamToken(Long userId) {
        if (userId == null) {
            return null;
        }
        String ssoToken = authService.getUpstreamToken(userId, ssoProps.getAppId());
        if (ssoToken == null) {
            return null;
        }
        try {
            IResult<UserInfoVO> res = userApi.userinfo("Bearer " + ssoToken);
            if (res == null || !ResultCode.SUCCESS.getCode().equals(res.getCode()) || res.getData() == null) {
                log.debug("SSO /userinfo 回源失败: userId={}, res={}", userId, res == null ? null : res.getCode());
                return null;
            }
            UserInfoVO info = res.getData();
            // 防串号：回源用户必须与请求的用户一致（上游数据属信任边界，宁可拒不可错）
            if (!userId.toString().equals(info.getSub())) {
                log.warn("SSO /userinfo 返回用户与请求不一致: 请求 userId={}, 返回 sub={}", userId, info.getSub());
                return null;
            }
            return toUser(info);
        } catch (Exception e) {
            // 网络故障等按"无法重建"返回 null（下游 401 重登），不吞成 5xx 也不放行
            log.debug("SSO /userinfo 回源异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取本应用的 client_credentials 应用 token（M2M 场景）.
     *
     * <p>token 缓存在实例内存（{@link SsoClientProperties#getAppTokenCacheTtl}，
     * 默认 1h，须小于 SSO 侧 app-timeout），过期后下次调用自动重取；
     * 缓存同时避免高频重取触发 SSO 的 appId 维度限流（默认 5 次/60s）。</p>
     *
     * <p><b>缓存命中失败兜底：</b>若持缓存 token 调 SSO 返回 401（SSO 侧提前踢人/
     * 实例重启丢会话），调 {@link #invalidateAppToken()} 后重试本方法即可。</p>
     *
     * <p>ponytail: 内存缓存不跨实例——多实例各自缓存各自换 token（SSO 允许并发会话，
     * 限流额度内无害）；换取期间的锁即 single-flight，正是想要的效果。
     * 实例很多且重取吵到限流时再升级 Redis 共享缓存。</p>
     *
     * @return 应用 token（loginId 为 {@code "app:"+appId}，无用户维度，不可调 /userinfo）
     * @throws BusinessException 换取失败（4001 凭证错误：appId/secret 不对或 SSO 拒绝）
     */
    public String getAppToken() {
        CachedAppToken cached = appToken;
        if (cached != null && !cached.expired()) {
            return cached.token();
        }
        synchronized (appTokenLock) {
            cached = appToken;
            if (cached != null && !cached.expired()) {
                return cached.token();
            }
            String token = fetchAppToken();
            appToken = new CachedAppToken(token,
                    System.currentTimeMillis() + ssoProps.getAppTokenCacheTtl().toMillis());
            return token;
        }
    }

    /**
     * 使缓存的应用 token 失效（持缓存 token 调 SSO 返回 401 时调用，下次 {@link #getAppToken} 强制重取）.
     */
    public void invalidateAppToken() {
        appToken = null;
    }

    /**
     * 调 SSO 用 client_credentials 换应用 token（无缓存，{@link #getAppToken} 的底层调用）.
     */
    private String fetchAppToken() {
        TokenRequestDTO req = new TokenRequestDTO();
        req.setGrantType("client_credentials");
        req.setAppId(ssoProps.getAppId());
        req.setAppSecret(ssoProps.getAppSecret());
        IResult<TokenVO> tokenRes = authApi.token(req);
        if (tokenRes == null || !ResultCode.SUCCESS.getCode().equals(tokenRes.getCode())
                || tokenRes.getData() == null) {
            String msg = tokenRes == null ? "SSO 响应为空" : tokenRes.getMsg();
            log.warn("SSO 换应用 token 失败: appId={}, msg={}", ssoProps.getAppId(), msg);
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "获取应用 token 失败: " + msg);
        }
        return tokenRes.getData().getAccessToken();
    }

    /**
     * SSO 用户信息 → base User（id/account/nickname/status）.
     *
     * <p>{@code sub} 非数字时抛 4001（凭证错误）而非 {@code NumberFormatException} 逸出成 500——
     * 与 {@code ssoLogin} 整体"上游响应有问题走 4001"的约定一致，
     * 前端可在登录页显示错误而非误判为服务端故障.</p>
     */
    private User toUser(UserInfoVO info) {
        User user = new User();
        try {
            user.setId(Long.valueOf(info.getSub()));
        } catch (NumberFormatException e) {
            // 上游 sub 非数字（数据异常/篡改）→ 4001，与 BAD_CREDENTIAL 语义对齐
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "SSO 用户标识非法: " + info.getSub());
        }
        user.setAccount(info.getAccount());
        user.setNickname(info.getName());
        user.setStatus(User.STATUS_ENABLED);
        return user;
    }

    /**
     * 缓存条目：token + 过期时间戳（毫秒）.
     */
    private record CachedAppToken(String token, long expireAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() >= expireAtMillis;
        }
    }
}

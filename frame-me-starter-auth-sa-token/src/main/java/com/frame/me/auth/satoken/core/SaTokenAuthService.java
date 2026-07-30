package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.frame.me.auth.core.AuthUserAuthenticator;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sa-Token 认证服务实现.
 *
 * <p>会话治理型实现：登录走 sa-token 标准 {@code StpUtil.login(id)}（原生写 Cookie），
 * 用户快照写入 Account-Session 缓存，读取时缓存优先、回源
 * {@link IAuthUserDetailsService#loadUserById(Long)}。</p>
 *
 * <p><b>调用约定：</b>{@code login} / {@code logout} / {@code refresh} 面向请求线程调用，
 * 依赖 sa-token 当前请求上下文（由官方 {@code SaTokenContextFilter} 初始化，order = -104，
 * Controller 阶段已就绪）；{@code validate} / {@code getUser} 走
 * {@code getLoginIdByToken} 纯 DAO 查询，上下文无关，可在非请求线程安全调用。</p>
 *
 * <p><b>JWT 留口：</b>当前仅做不透明 token 的 Redis 会话模式，代码不假设 token 格式；
 * 未来接入 {@code StpLogicJwtForSimple} 时只需替换 {@code StpUtil.setStpLogic(...)}，本类无需改动。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class SaTokenAuthService implements IAuthService {

    /**
     * Account-Session 中缓存用户快照的 key（值为 JSON 字符串，保证跨序列化后端类型稳定）.
     */
    static final String SESSION_USER_KEY = "user";

    private final IAuthUserDetailsService userDetailsService;

    @Override
    public String login(String account, String password) {
        User user = AuthUserAuthenticator.authenticate(userDetailsService, account, password);
        // 标准登录：原生写 Cookie（sa-token.is-read-cookie=true 时）；login 后当前上下文即该会话
        StpUtil.login(user.getId());
        cacheUser(user.getId(), user, null);
        return StpUtil.getTokenValue();
    }

    /**
     * 注销当前请求 token（原生清 Cookie + 注销当前会话）.
     *
     * <p>与 JWT 实现语义一致：Controller 传入的 credential 本来就是当前请求的 token，
     * 本方法仅对其判空/debug，实际注销对象为 sa-token 当前上下文 token。</p>
     */
    @Override
    public void logout(String credential) {
        if (credential == null || credential.isBlank()) {
            return;
        }
        try {
            StpUtil.logout();
        } catch (Exception e) {
            log.debug("Sa-Token 登出异常（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 按用户 ID 强制登出：注销该 loginId 的所有会话与 Token.
     *
     * <p>即使该用户当前未登录也是安全 no-op；Redis 后端下会同步清除 Redis 中的 session，
     * 所有节点立即生效。</p>
     */
    @Override
    public void logoutByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            StpUtil.logout(userId);
            log.debug("管理员强制登出用户: userId={}", userId);
        } catch (NotLoginException e) {
            // 用户未登录或 loginId 不存在，无需强制登出
            log.debug("用户未登录或不存在，跳过强制登出: userId={}", userId);
        } catch (Exception e) {
            log.warn("Sa-Token 强制登出失败: userId={}", userId, e);
            throw new BusinessException(ResultCode.ERROR, "强制登出失败");
        }
    }

    @Override
    public String refresh(String credential) {
        Object loginId = StpUtil.getLoginIdByToken(credential);
        if (loginId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token 已失效");
        }
        // 续期对象与校验对象统一为传入 credential：无参重载作用于请求上下文 token，
        // 非请求线程的 SPI 调用方会出现「校验 A 续期 B」或上下文无 token 抛 NotLoginException。
        // timeout 秒数取原生配置；token 值不变，Cookie 客户端无需重写 Cookie
        StpUtil.renewTimeout(credential, SaManager.getConfig().getTimeout());
        StpUtil.stpLogic.updateLastActiveToNow(credential);
        return credential;
    }

    @Override
    public boolean validate(String credential) {
        return credential != null && !credential.isBlank() && StpUtil.getLoginIdByToken(credential) != null;
    }

    @Override
    public User getUser(String credential) {
        if (credential == null || credential.isBlank()) {
            return null;
        }
        Object loginId = StpUtil.getLoginIdByToken(credential);
        if (loginId == null) {
            return null;
        }
        return loadUserByLoginId(loginId);
    }

    /**
     * 按 loginId 读取用户：Session 缓存优先，缺失时回源并回写缓存.
     *
     * @param loginId sa-token 登录 ID
     * @return 用户信息，用户不存在时返回 {@code null}
     */
    User loadUserByLoginId(Object loginId) {
        // no-create：读路径不允许产生写副作用（单参版会在 session 缺失时创建空 session），
        // 缺失时回源数据源，由 cacheUser 决定能否回写
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session != null) {
            User cached = readUser(session.get(SESSION_USER_KEY));
            if (cached != null) {
                return cached;
            }
        }
        User user = userDetailsService.loadUserById(Long.valueOf(String.valueOf(loginId)));
        if (user != null) {
            // ponytail: 复用已获取的 session，避免重复 Redis getSessionByLoginId 查询
            cacheUser(loginId, user, session);
        }
        return user;
    }

    /**
     * 把用户快照写入 Account-Session（JSON 字符串形式，跨内存/Redis 后端类型稳定）.
     *
     * <p>session 为 null（已过期/被清除）时跳过回写——读路径不创建 session，
     * 该罕见边缘下每次请求回源数据源，语义上优于凭空创建空会话。</p>
     *
     * @param loginId sa-token 登录 ID
     * @param user    用户信息
     * @param session 已获取的 Account-Session（可为 null，此时跳过回写）
     */
    private void cacheUser(Object loginId, User user, SaSession session) {
        try {
            if (session == null) {
                session = StpUtil.getSessionByLoginId(loginId, false);
            }
            if (session != null) {
                session.set(SESSION_USER_KEY, JSON.toJSONString(user));
            }
        } catch (Exception e) {
            log.debug("写入 Sa-Token 用户缓存失败（忽略，下次读取将回源）: {}", e.getMessage());
        }
    }

    /**
     * 从 Session 值还原 User：JSON 字符串反序列化；兼容直接放入的 User 对象（内存 DAO）.
     */
    static User readUser(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof User user) {
            return user;
        }
        if (raw instanceof String json) {
            return JSON.parseObject(json, User.class);
        }
        return null;
    }
}

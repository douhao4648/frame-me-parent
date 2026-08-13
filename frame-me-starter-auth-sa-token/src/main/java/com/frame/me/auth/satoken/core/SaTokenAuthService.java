package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.frame.me.auth.core.AuthUserAuthenticator;
import com.frame.me.auth.satoken.config.SaTokenAuthProperties;
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
 * <p>会话治理型实现：登录走 sa-token 标准 {@code StpLogic.login(id)}（原生写 Cookie），
 * 用户快照写入 Account-Session 缓存，读取时缓存优先、回源
 * {@link IAuthUserDetailsService#loadUserById(Long)}。</p>
 *
 * <p><b>账号体系：</b>默认操作 {@code StpUtil} 的 {@code login} 体系；配置
 * {@code me.auth.sa-token.logic-type} 为非默认值时全部动作走
 * {@code SaManager.getStpLogic(logicType)}（sa-token 多账号体系），
 * 与默认体系在 Redis 中以 {@code {tokenName}:{logicType}:*} 命名空间隔离。</p>
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

    /**
     * Account-Session 中记录登录时间戳（毫秒）的 key，用于 refresh 绝对寿命上限校验.
     */
    static final String SESSION_LOGIN_TIME_KEY = "loginTime";

    /**
     * Account-Session 中存储上游 IdP token 的 key 前缀（实际 key 为前缀 + appId，
     * 按上游应用隔离；RP 场景留存，随会话同生共死）.
     */
    static final String SESSION_UPSTREAM_TOKEN_KEY_PREFIX = "upstreamToken:";

    private final IAuthUserDetailsService userDetailsService;

    private final SaTokenAuthProperties properties;

    /**
     * 解析本服务使用的 {@link StpLogic}：默认 {@code login} 体系即 {@code StpUtil.stpLogic}；
     * 配置 {@code me.auth.sa-token.logic-type} 为非默认值时走 sa-token 多账号体系
     * （{@code SaManager.getStpLogic} 不存在则自动创建并注册）.
     */
    private StpLogic stpLogic() {
        return SaManager.getStpLogic(properties.getLogicType());
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

    @Override
    public String login(String account, String password) {
        User user = AuthUserAuthenticator.authenticate(userDetailsService, account, password);
        // 标准登录：原生写 Cookie（sa-token.is-read-cookie=true 时）；login 后当前上下文即该会话
        stpLogic().login(user.getId());
        markLoginTime(stpLogic(), user.getId());
        cacheUser(user.getId(), user, null);
        return stpLogic().getTokenValue();
    }

    /**
     * 按已知用户直接建立 sa-token 会话（RP 场景：身份已由外部 IdP 验证，无需密码校验）.
     *
     * <p>覆盖 {@link IAuthService#loginByUser(User)}：供 SSO 下游等"code 换用户后建本地 session"
     * 场景使用。{@link #login(String, String)} 走账号密码流程（{@link AuthUserAuthenticator}），
     * 不适用于 RP。本方法直接 {@code StpUtil.login} + {@code cacheUser}，语义与 {@code login}
     * 一致，仅跳过密码校验环节；用户快照同样写入 Account-Session，后续
     * {@link #getUser(String)} 从 session 缓存读取。</p>
     *
     * @param user 已认证用户（由外部 IdP 提供身份，id 必填）
     * @return sa-token 会话 token
     * @throws com.frame.me.base.exception.BusinessException 用户信息无效或账号已禁用（4001 凭证错误）
     */
    @Override
    public String loginByUser(User user) {
        if (user == null || user.getId() == null) {
            // 凭证错误（4001）：RP 换会话流程中拿到的用户有问题，前端留登录页显示错误
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "用户信息无效");
        }
        if (!User.STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "账号已被禁用");
        }
        stpLogic().login(user.getId());
        markLoginTime(stpLogic(), user.getId());
        cacheUser(user.getId(), user, null);
        return stpLogic().getTokenValue();
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
            stpLogic().logout();
        } catch (NotLoginException e) {
            // 未登录场景：登出本就无意义，静默忽略
            log.debug("Sa-Token 登出时未登录（忽略）: {}", e.getMessage());
        } catch (Exception e) {
            // 基础设施故障（如 Redis 不可达）：会话未真正注销，token 仍可用，
            // 不吞成成功——log.warn 提示并上抛，让调用方感知（对齐 JWT 模块 refresh 的不吞语义）
            log.warn("Sa-Token 登出失败（会话可能未注销，token 仍有效）: {}", e.getMessage());
            throw e;
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
            stpLogic().logout(userId);
            log.debug("管理员强制登出用户: userId={}", userId);
        } catch (NotLoginException e) {
            // 用户未登录或 loginId 不存在，无需强制登出
            log.debug("用户未登录或不存在，跳过强制登出: userId={}", userId);
        } catch (Exception e) {
            log.warn("Sa-Token 强制登出失败: userId={}", userId, e);
            throw new BusinessException(ResultCode.ERROR, "强制登出失败", e);
        }
    }

    @Override
    public String refresh(String credential) {
        Object loginId = stpLogic().getLoginIdByToken(credential);
        if (loginId == null) {
            // 凭证错误（4001）：refresh 流程的凭证失效，前端留登录页显示错误，避免与"会话缺失"401 混淆导致循环重定向
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Token 已失效");
        }
        // 续期对象与校验对象统一为传入 credential：无参重载作用于请求上下文 token，
        // 非请求线程的 SPI 调用方会出现「校验 A 续期 B」或上下文无 token 抛 NotLoginException。
        long timeout = SaManager.getConfig().getTimeout();
        SaSession session = stpLogic().getSessionByLoginId(loginId, false);
        // 绝对寿命闸门：滑动续期不设上限时，被偷的 token 可无限续命、会话永不过期；
        // 超过 max-lifetime 拒绝续期（4001），强制重新登录
        long maxLifetime = properties.getMaxLifetime();
        if (maxLifetime > 0) {
            Object loginTime = session == null ? null : session.get(SESSION_LOGIN_TIME_KEY);
            if (loginTime == null) {
                // ponytail: 存量会话无登录时间戳，补记当前时间、从本次起算（一次宽限窗口）
                if (session != null) {
                    session.set(SESSION_LOGIN_TIME_KEY, System.currentTimeMillis());
                }
            } else if (System.currentTimeMillis() - Long.parseLong(String.valueOf(loginTime)) > maxLifetime * 1000L) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "会话已达最长有效期，请重新登录");
            }
        }
        // renewTimeout 内部已一并续期：token TTL、Token-Session、Account-Session（updateMinTimeout，
        // 只延不缩）、last-active key 的 TTL（仅 active-timeout 开启时）——故此处不再手动续 session。
        // updateLastActiveToNow 才是重置最后活跃时间戳（renewTimeout 只续该 key 的 TTL、不刷值），
        // 仅在配置 sa-token.active-timeout 时有实际意义，关闭时为无害 no-op。
        // ponytail: 两步非原子（sa-token API 限制），Redis 中断可致部分续期成功；
        // 影响面小——最坏仅某项 TTL 略微偏差，下次请求即可自我修复
        stpLogic().renewTimeout(credential, timeout);
        stpLogic().updateLastActiveToNow(credential);
        return credential;
    }

    @Override
    public boolean validate(String credential) {
        return credential != null && !credential.isBlank() && stpLogic().getLoginIdByToken(credential) != null;
    }

    /**
     * 把上游 IdP token 写入 Account-Session（RP 场景留存，按 appId 分 key 隔离）.
     *
     * <p>无需显式清除：登出/被踢会注销 Account-Session，token 随会话一并销毁。
     * session 缺失（已过期/被清）时跳过写入，对齐 {@code cacheUser} 的 no-create 语义。</p>
     */
    @Override
    public void storeUpstreamToken(Long userId, String appId, String upstreamToken) {
        if (userId == null || appId == null || appId.isBlank()
                || upstreamToken == null || upstreamToken.isBlank()) {
            return;
        }
        try {
            SaSession session = stpLogic().getSessionByLoginId(userId, false);
            if (session != null) {
                session.set(SESSION_UPSTREAM_TOKEN_KEY_PREFIX + appId, upstreamToken);
            }
        } catch (Exception e) {
            log.debug("写入上游 IdP token 失败（忽略）: userId={}, appId={}, {}", userId, appId, e.getMessage());
        }
    }

    @Override
    public String getUpstreamToken(Long userId, String appId) {
        if (userId == null || appId == null || appId.isBlank()) {
            return null;
        }
        SaSession session = stpLogic().getSessionByLoginId(userId, false);
        if (session == null) {
            return null;
        }
        Object value = session.get(SESSION_UPSTREAM_TOKEN_KEY_PREFIX + appId);
        return value == null ? null : value.toString();
    }

    @Override
    public User getUser(String credential) {
        if (credential == null || credential.isBlank()) {
            return null;
        }
        Object loginId = stpLogic().getLoginIdByToken(credential);
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
        SaSession session = stpLogic().getSessionByLoginId(loginId, false);
        if (session != null) {
            User cached = readUser(session.get(SESSION_USER_KEY));
            if (cached != null) {
                return cached;
            }
        }
        User user;
        try {
            user = userDetailsService.loadUserById(Long.valueOf(String.valueOf(loginId)));
        } catch (NumberFormatException e) {
            // 非数字 loginId（如 SSO client_credentials 的 "app:"+appId 应用主体）：
            // 无用户维度，按"无此用户"返回 null，调用方按未登录/无权限处理，
            // 不能让 NumberFormatException 逸出过滤器层变成 500
            log.debug("loginId 非数字用户 ID，按无用户处理: {}", loginId);
            return null;
        }
        if (user != null && !User.STATUS_ENABLED.equals(user.getStatus())) {
            log.debug("用户已禁用，拒绝加载: userId={}", user.getId());
            return null;
        }
        if (user != null) {
            // ponytail: 复用已获取的 session，避免重复 Redis getSessionByLoginId 查询
            cacheUser(loginId, user, session);
        }
        return user;
    }

    /**
     * 把登录时间戳写入 Account-Session，供 {@link #refresh(String)} 做绝对寿命上限校验.
     *
     * <p>public static：不走 {@code login}/{@code loginByUser} 的建会话路径
     * （如 SSO 服务用 {@code StpUtil.createLoginSession} 的 code 换 token 端点）
     * 也应在签发后调用，否则该会话的绝对寿命会从第一次续期起算（宽限路径）。</p>
     *
     * @param loginId sa-token 登录 ID
     */
    public static void markLoginTime(Object loginId) {
        markLoginTime(StpUtil.stpLogic, loginId);
    }

    /**
     * 把登录时间戳写入指定账号体系的 Account-Session，语义同 {@link #markLoginTime(Object)}.
     *
     * @param logic   目标账号体系的 {@link StpLogic}（如 SSO 的 {@code sso} 体系）
     * @param loginId sa-token 登录 ID
     */
    public static void markLoginTime(StpLogic logic, Object loginId) {
        try {
            SaSession session = logic.getSessionByLoginId(loginId, false);
            if (session != null) {
                session.set(SESSION_LOGIN_TIME_KEY, System.currentTimeMillis());
            }
        } catch (Exception e) {
            // 时间戳写入失败不阻断登录；refresh 侧对缺失值会补记宽限
            log.debug("写入 Sa-Token 登录时间戳失败（忽略）: {}", e.getMessage());
        }
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
                session = stpLogic().getSessionByLoginId(loginId, false);
            }
            if (session != null) {
                session.set(SESSION_USER_KEY, JSON.toJSONString(user));
            }
        } catch (Exception e) {
            log.debug("写入 Sa-Token 用户缓存失败（忽略，下次读取将回源）: {}", e.getMessage());
        }
    }
}

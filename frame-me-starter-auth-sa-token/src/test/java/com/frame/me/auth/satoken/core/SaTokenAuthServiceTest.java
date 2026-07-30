package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.util.PasswordUtils;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SaTokenAuthService} 单元测试.
 *
 * <p>sa-token 的 {@code SaManager} 在未 Spring 装配时自动退回默认内存 DAO
 * （{@code SaTokenDaoDefaultImpl}）与默认配置，故可脱离容器跑全链路。
 * login / logout / refresh 面向请求线程、依赖 sa-token 当前请求上下文，
 * 用 sa-token 官方 {@link SaTokenContextMockUtil} 搭建 Mock 上下文；
 * validate / getUser 上下文无关，同样在该上下文下运行不受影响。
 * sa-token 状态为 JVM 级静态，各用例使用互不相同且不与真实业务冲突的用户 ID 隔离。</p>
 *
 * @author frame-me
 */
class SaTokenAuthServiceTest {

    private static final String RAW_PASSWORD = "123456";

    private final IAuthUserDetailsService userDetailsService = mock(IAuthUserDetailsService.class);
    private final SaTokenAuthService authService = new SaTokenAuthService(userDetailsService);

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
    }

    /**
     * 构造带 BCrypt 密码的测试用户，并打通查询与密码校验桩.
     */
    private User stubUser(long userId, String account) {
        User user = new User();
        user.setId(userId);
        user.setAccount(account);
        user.setPassword(PasswordUtils.encode(RAW_PASSWORD));
        when(userDetailsService.loadUserByAccount(account)).thenReturn(user);
        when(userDetailsService.loadUserById(userId)).thenReturn(user);
        when(userDetailsService.matches(anyString(), anyString())).thenAnswer(invocation ->
                PasswordUtils.matches(invocation.getArgument(0), invocation.getArgument(1)));
        return user;
    }

    /**
     * 登录成功 → 返回 token；validate/getUser 全链路；用户快照走 Session 缓存，不回源 loadUserById.
     */
    @Test
    void loginSuccess_validateAndGetUser_sessionCacheHit() {
        User user = stubUser(91001L, "alice");

        String token = authService.login("alice", RAW_PASSWORD);
        assertThat(token).isNotBlank();
        assertThat(authService.validate(token)).isTrue();

        User resolved = authService.getUser(token);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getId()).isEqualTo(91001L);
        assertThat(resolved.getAccount()).isEqualTo("alice");

        // 登录时已写入 Session 缓存，getUser 命中缓存，不回源
        verify(userDetailsService, never()).loadUserById(anyLong());
        assertThat(user.getId()).isEqualTo(resolved.getId());
    }

    /**
     * 账号不存在 → 401 业务异常.
     */
    @Test
    void loginUnknownAccount_throwsUnauthorized() {
        when(userDetailsService.loadUserByAccount("nobody")).thenReturn(null);

        assertThatThrownBy(() -> authService.login("nobody", RAW_PASSWORD))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    /**
     * 密码错误 → 401 业务异常.
     */
    @Test
    void loginWrongPassword_throwsUnauthorized() {
        stubUser(91002L, "bob");

        assertThatThrownBy(() -> authService.login("bob", "wrong-password"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    /**
     * 登出（注销当前请求 token）后 token 失效；登出空凭证与非法 token 静默忽略.
     */
    @Test
    void logout_invalidatesToken_andToleratesBadInput() {
        stubUser(91003L, "carol");
        String token = authService.login("carol", RAW_PASSWORD);
        assertThat(authService.validate(token)).isTrue();

        authService.logout(token);
        assertThat(authService.validate(token)).isFalse();
        assertThat(authService.getUser(token)).isNull();

        authService.logout(null);
        authService.logout("  ");
        authService.logout("not-a-real-token");
    }

    /**
     * 按用户 ID 强制登出：该用户的所有 token 失效；userId 为空时静默忽略.
     */
    @Test
    void logoutByUserId_invalidatesAllSessions_andToleratesNull() {
        stubUser(91007L, "grace");
        String token = authService.login("grace", RAW_PASSWORD);
        assertThat(authService.validate(token)).isTrue();

        authService.logoutByUserId(91007L);

        assertThat(authService.validate(token)).isFalse();
        assertThat(authService.getUser(token)).isNull();

        authService.logoutByUserId(null);
    }

    /**
     * 续期：有效 token 续期后仍有效且 token 不变；失效 token 续期抛 401.
     */
    @Test
    void refresh_validToken_keepsTokenAndValidity() {
        stubUser(91004L, "dave");
        String token = authService.login("dave", RAW_PASSWORD);

        String refreshed = authService.refresh(token);
        assertThat(refreshed).isEqualTo(token);
        assertThat(authService.validate(token)).isTrue();

        assertThatThrownBy(() -> authService.refresh("invalid-token-value"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    /**
     * 续期目标必须是传入的 credential 而非上下文 token：
     * 续期 A 后 A 的 timeout 被拉满，同上下文中的 B 保持不变.
     */
    @Test
    void refresh_targetsCredentialNotContextToken() {
        long originalTimeout = SaManager.getConfig().getTimeout();
        SaManager.getConfig().setTimeout(100);
        try {
            stubUser(91006L, "frank");
            stubUser(91007L, "grace");
            String tokenA = authService.login("frank", RAW_PASSWORD);
            String tokenB = authService.login("grace", RAW_PASSWORD);
            // 人为压低两者 timeout，观察续期落在哪个 token 上
            StpUtil.renewTimeout(tokenA, 50);
            StpUtil.renewTimeout(tokenB, 70);

            authService.refresh(tokenA);

            assertThat(StpUtil.getTokenTimeout(tokenA)).isGreaterThan(90);
            assertThat(StpUtil.getTokenTimeout(tokenB)).isLessThanOrEqualTo(70);
        } finally {
            SaManager.getConfig().setTimeout(originalTimeout);
        }
    }

    /**
     * Account-Session 整体缺失（过期/被清除，token 仍有效）时：
     * getUser 回源数据源返回用户，但读路径不得重建空 session（no-create 语义）.
     */
    @Test
    void getUser_sessionAbsent_fallsBackWithoutCreatingSession() {
        stubUser(91008L, "henry");
        String token = authService.login("henry", RAW_PASSWORD);

        // 物理删除整个 Account-Session
        SaSession session = StpUtil.getSessionByLoginId(91008L, false);
        SaManager.getSaTokenDao().deleteObject(session.getId());
        assertThat(StpUtil.getSessionByLoginId(91008L, false)).isNull();

        User resolved = authService.getUser(token);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getAccount()).isEqualTo("henry");
        assertThat(StpUtil.getSessionByLoginId(91008L, false)).isNull();
    }

    /**
     * Session 缓存缺失（如序列化后端切换后旧数据不可读）时回源 loadUserById.
     */
    @Test
    void getUser_sessionCacheMiss_fallsBackToLoadUserById() {
        stubUser(91005L, "erin");
        String token = authService.login("erin", RAW_PASSWORD);

        // 清掉 Session 中的用户快照，模拟缓存缺失
        StpUtil.getSessionByLoginId(91005L).delete(SaTokenAuthService.SESSION_USER_KEY);

        User resolved = authService.getUser(token);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getAccount()).isEqualTo("erin");
        verify(userDetailsService).loadUserById(91005L);
    }

    /**
     * 空 / 空白 / 非法凭证：validate 为 false，getUser 返回 null.
     */
    @Test
    void emptyOrInvalidCredential() {
        assertThat(authService.validate(null)).isFalse();
        assertThat(authService.validate("")).isFalse();
        assertThat(authService.validate("  ")).isFalse();
        assertThat(authService.getUser(null)).isNull();
        assertThat(authService.getUser("")).isNull();
        assertThat(authService.getUser("not-exist-token")).isNull();
    }
}

package com.frame.me.sso.infrastructure.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpLogic;

/**
 * SSO 账号体系的 {@link StpLogic} 入口（sa-token 多账号体系）.
 *
 * <p>SSO 服务整体使用独立的 {@value #TYPE} 账号体系：Redis key 为
 * {@code {tokenName}:sso:token|session:*}（如 {@code satoken:sso:token:xxx}），
 * 与默认 {@code login} 体系（{@code satoken:login:*}，audit/tester 等下游服务）
 * 在同一 Redis 中命名空间隔离、互不串号。请求头/Cookie 名仍取全局
 * {@code sa-token.token-name}，两体系共用同一通道名但 key 空间不同。</p>
 *
 * <p>静态字段初始化即触发 {@code SaManager.getStpLogic(TYPE)} 的自动创建并注册，
 * 供 {@code @SaCheck*(type = SsoStpUtil.TYPE)} 注解按 type 查找（isCreate=false，
 * 未注册会抛异常；starter 装配期也会按 {@code me.auth.sa-token.logic-type} 解析一次，
 * 双保险）。</p>
 *
 * @author frame-me
 */
public final class SsoStpUtil {

    /**
     * SSO 账号体系标识（sa-token 多账号 loginType）.
     *
     * <p>硬编码常量而非配置项：{@code @SaCheck*(type = ...)} 注解属性须编译期常量，
     * 配置值进不了注解（sa-token 官方多账号模式同样硬编码）。与 yml 的
     * {@code me.auth.sa-token.logic-type} 是同一件事的两次声明，
     * {@code SsoConfiguration} 启动期校验两者一致（fail-fast 防静默漂移）。</p>
     */
    public static final String TYPE = "sso";

    /**
     * SSO 体系 StpLogic：本服务所有登录/登出/验 token 动作的统一入口.
     */
    public static final StpLogic STP_LOGIC = SaManager.getStpLogic(TYPE);

    private SsoStpUtil() {
    }
}

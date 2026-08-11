package com.frame.me.auth.core;

import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 空操作用户解析器：不解析任何身份，所有请求按匿名处理.
 *
 * <p>显式配置 {@code me.auth.trusted-header.enabled=false} 时装配，用于不需要任何
 * 用户解析行为的场景（如纯内部任务服务）：正常启动、受保护端点一律 401，
 * 仅 {@code @Anonymous} 与白名单端点可达。不配置该属性（unset）仍保持
 * fail-closed——缺少 resolver 时启动直接失败，防漏配静默裸奔。</p>
 *
 * @author frame-me
 */
public class NoOpAuthUserResolver implements IAuthUserResolver {

    @Override
    public User resolve(HttpServletRequest request) {
        return null;
    }
}

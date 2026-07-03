package com.frame.me.auth.propagation;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证上下文异步任务装饰器.
 *
 * <p>当 {@code me.auth.propagate.async.enabled=true} 时，在提交异步任务前捕获
 * {@link AuthContext} 用户以及当前请求的认证头，并在异步线程执行前恢复；关闭时原样执行。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class AuthContextTaskDecorator implements TaskDecorator {

    private final AuthProperties properties;

    @Override
    public Runnable decorate(Runnable runnable) {
        if (!Boolean.TRUE.equals(properties.getPropagate().getAsync().getEnabled())) {
            return runnable;
        }

        User user = AuthContext.getUser();
        Map<String, String> headers = captureHeaders();
        if (user == null && (headers == null || headers.isEmpty())) {
            return runnable;
        }

        return () -> {
            try {
                if (user != null) {
                    AuthContext.setUser(user);
                    log.debug("认证上下文已传播到异步线程: userId={}", user.getId());
                }
                if (headers != null && !headers.isEmpty()) {
                    AuthPropagationHolder.setHeaders(headers);
                    log.debug("认证头已传播到异步线程: headers={}", headers.keySet());
                }
                runnable.run();
            } finally {
                AuthContext.clear();
                AuthPropagationHolder.clear();
            }
        };
    }

    /**
     * 从当前请求上下文捕获需要传播的请求头.
     *
     * @return 捕获到的请求头，无内容时返回 {@code null}
     */
    private Map<String, String> captureHeaders() {
        List<String> headerNames = properties.getPropagate().getHeaders();
        if (headerNames == null || headerNames.isEmpty()) {
            return null;
        }

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }

        HttpServletRequest request = servletAttributes.getRequest();
        Map<String, String> captured = new HashMap<>();
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isEmpty()) {
                continue;
            }
            String value = request.getHeader(headerName);
            if (value != null && !value.isEmpty()) {
                captured.put(headerName, value);
            }
        }
        return captured.isEmpty() ? null : captured;
    }
}

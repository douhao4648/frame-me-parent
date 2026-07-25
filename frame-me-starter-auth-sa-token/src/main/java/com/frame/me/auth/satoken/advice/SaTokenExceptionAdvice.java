package com.frame.me.auth.satoken.advice;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 异常到 401/403 语义的映射.
 *
 * <p>只处理 Controller 阶段由 sa-token 抛出的异常（{@code SaInterceptor} 路径规则校验、
 * {@code @SaCheck*} 注解校验）；Filter 层的 401 仍由
 * {@code AuthFilter} + {@code IFilterErrorResponseWriter} 输出，与本 Advice 无关。</p>
 *
 * <p>标注 {@link Order#HIGHEST_PRECEDENCE}：{@code GlobalExceptionHandler} 持有通用
 * {@code Exception} 处理器（无 {@code @Order}，默认最低优先级），本 Advice 必须排在它之前，
 * 否则 sa-token 异常会被通用处理器按 500 吞掉。</p>
 *
 * @author frame-me
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SaTokenExceptionAdvice {

    /**
     * 未登录 / Token 无效 / 过期 / 被顶下线 / 被踢下线 / 被冻结 → 401.
     */
    @ExceptionHandler(NotLoginException.class)
    public IResult<Void> handleNotLoginException(NotLoginException e) {
        log.warn("Sa-Token 未登录: {}", e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED);
    }

    /**
     * 无权限码 → 403.
     */
    @ExceptionHandler(NotPermissionException.class)
    public IResult<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("Sa-Token 无权限: {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }

    /**
     * 无角色 → 403.
     */
    @ExceptionHandler(NotRoleException.class)
    public IResult<Void> handleNotRoleException(NotRoleException e) {
        log.warn("Sa-Token 无角色: {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }

    /**
     * 账号 / 服务被封禁 → 403.
     */
    @ExceptionHandler(DisableServiceException.class)
    public IResult<Void> handleDisableServiceException(DisableServiceException e) {
        log.warn("Sa-Token 服务封禁: {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }
}

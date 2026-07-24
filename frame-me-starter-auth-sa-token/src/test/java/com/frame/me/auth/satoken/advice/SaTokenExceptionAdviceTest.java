package com.frame.me.auth.satoken.advice;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.frame.me.api.result.IResult;
import com.frame.me.base.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SaTokenExceptionAdvice} 异常映射测试：sa-token 异常 → 401/403 语义.
 *
 * @author frame-me
 */
class SaTokenExceptionAdviceTest {

    private final SaTokenExceptionAdvice advice = new SaTokenExceptionAdvice();

    /**
     * 未登录 / token 无效 → 401.
     */
    @Test
    void notLoginException_mapsTo401() {
        NotLoginException exception = new NotLoginException(
                NotLoginException.INVALID_TOKEN_MESSAGE, "login", NotLoginException.INVALID_TOKEN);
        IResult<Void> result = advice.handleNotLoginException(exception);
        assertThat(result.getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
        assertThat(result.getMsg()).isEqualTo(NotLoginException.INVALID_TOKEN_MESSAGE);
    }

    /**
     * 无权限码 → 403.
     */
    @Test
    void notPermissionException_mapsTo403() {
        IResult<Void> result = advice.handleNotPermissionException(new NotPermissionException("order:read"));
        assertThat(result.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    /**
     * 无角色 → 403.
     */
    @Test
    void notRoleException_mapsTo403() {
        IResult<Void> result = advice.handleNotRoleException(new NotRoleException("admin"));
        assertThat(result.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    /**
     * 服务封禁 → 403.
     */
    @Test
    void disableServiceException_mapsTo403() {
        DisableServiceException exception =
                new DisableServiceException("login", 1L, "comment", 1, 2, 3600L);
        IResult<Void> result = advice.handleDisableServiceException(exception);
        assertThat(result.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
    }
}

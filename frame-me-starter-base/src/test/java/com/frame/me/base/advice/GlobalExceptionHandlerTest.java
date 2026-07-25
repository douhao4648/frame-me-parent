package com.frame.me.base.advice;

import com.frame.me.api.result.IResult;
import com.frame.me.base.config.ExceptionProperties;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} 单元测试.
 *
 * @author frame-me
 */
class GlobalExceptionHandlerTest {

    private final ExceptionProperties enabled = new ExceptionProperties();
    private final ExceptionProperties disabled = new ExceptionProperties();

    {
        enabled.setIncludeStacktrace(true);
        disabled.setIncludeStacktrace(false);
    }

    @Test
    void businessException_includeStacktraceWhenEnabled() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(enabled);
        IResult<Void> result = handler.handleBusinessException(
                new BusinessException(ResultCode.BAD_REQUEST, "业务规则不满足"));
        assertThat(result.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(result.getMsg()).isEqualTo("业务规则不满足");
        assertThat(result.getErr()).contains("BusinessException");
    }

    @Test
    void businessException_excludeStacktraceWhenDisabled() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(disabled);
        IResult<Void> result = handler.handleBusinessException(
                new BusinessException(ResultCode.BAD_REQUEST, "业务规则不满足"));
        assertThat(result.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(result.getMsg()).isEqualTo("业务规则不满足");
        assertThat(result.getErr()).isNull();
    }

    @Test
    void internalException_excludeStacktraceWhenDisabled() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(disabled);
        IResult<Void> result = handler.handleInternalException(
                new InternalException("内部错误"));
        assertThat(result.getCode()).isEqualTo(ResultCode.ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo("内部错误");
        assertThat(result.getErr()).isNull();
    }

    @Test
    void genericException_excludeStacktraceWhenDisabled() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(disabled);
        IResult<Void> result = handler.handleException(new RuntimeException("系统挂了"));
        assertThat(result.getCode()).isEqualTo(ResultCode.ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo("系统挂了");
        assertThat(result.getErr()).isNull();
    }
}

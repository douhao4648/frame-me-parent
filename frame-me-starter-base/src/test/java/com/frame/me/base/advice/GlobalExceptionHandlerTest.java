package com.frame.me.base.advice;

import com.frame.me.api.result.IResult;
import com.frame.me.base.config.ExceptionProperties;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        enabled.setMaskUnknownMessage(false);
        disabled.setIncludeStacktrace(false);
        disabled.setMaskUnknownMessage(false);
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
        IResult<Void> result = handler.handleException(new RuntimeException("系统挂了"), null);
        assertThat(result.getCode()).isEqualTo(ResultCode.ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo("系统挂了");
        assertThat(result.getErr()).isNull();
    }

    /**
     * Spring 7 的 MVC 请求侧异常族（NoResourceFoundException 等）仅实现 ErrorResponse、
     * 不再继承 ResponseStatusException，落入兜底分支后应按其自带状态码透传（404），而非 500.
     */
    @Test
    void noResourceFound_transparent404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ExceptionProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        IResult<Void> result = handler.handleException(
                new NoResourceFoundException(HttpMethod.GET,
                        ".well-known/appspecific/com.chrome.devtools.json",
                        "/.well-known/appspecific/com.chrome.devtools.json"),
                response);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMsg()).contains("No static resource");
    }

    /**
     * 兜底异常默认返回通用文案（maskUnknownMessage 默认开启），真实异常信息只进服务端日志.
     */
    @Test
    void genericException_masksMessageByDefault() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ExceptionProperties());
        IResult<Void> result = handler.handleException(new RuntimeException("系统挂了"), null);
        assertThat(result.getMsg()).isEqualTo(ResultCode.ERROR.getMsg());
    }

    /**
     * 开启 mask-unknown-message 后，兜底异常对外收敛为通用文案，内部细节只进日志.
     */
    @Test
    void genericException_maskUnknownMessageWhenEnabled() {
        ExceptionProperties masked = new ExceptionProperties();
        masked.setMaskUnknownMessage(true);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(masked);
        IResult<Void> result = handler.handleException(
                new RuntimeException("Table 't_secret_user' doesn't exist"), null);
        assertThat(result.getCode()).isEqualTo(ResultCode.ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo(ResultCode.ERROR.getMsg());
        assertThat(result.getMsg()).doesNotContain("t_secret_user");
    }

    /**
     * includeStacktrace 默认关闭，避免堆栈随响应体泄漏.
     */
    @Test
    void includeStacktrace_defaultsToFalse() {
        assertThat(new ExceptionProperties().isIncludeStacktrace()).isFalse();
    }

    /**
     * maskUnknownMessage 默认开启，防止内部异常信息泄漏.
     */
    @Test
    void maskUnknownMessage_defaultsToTrue() {
        assertThat(new ExceptionProperties().isMaskUnknownMessage()).isTrue();
    }
}

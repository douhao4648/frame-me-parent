package com.frame.me.base.advice;

import com.frame.me.api.result.IResult;
import com.frame.me.base.config.ExceptionProperties;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * 全局异常处理.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ExceptionProperties exceptionProperties;

    public GlobalExceptionHandler(ExceptionProperties exceptionProperties) {
        this.exceptionProperties = exceptionProperties;
    }

    /**
     * 处理业务异常.
     */
    @ExceptionHandler(BusinessException.class)
    public IResult<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return errorResult(e.getCode(), e.getMessage(), e);
    }

    /**
     * 处理内部异常.
     */
    @ExceptionHandler(InternalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public IResult<Void> handleInternalException(InternalException e) {
        log.error("内部异常: {}", e.getMessage(), e);
        return errorResult(e.getCode(), e.getMessage(), e);
    }

    /**
     * 处理参数校验异常（@RequestBody）.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public IResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 处理参数校验异常（@PathVariable / @RequestParam）.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public IResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 处理参数绑定校验异常（表单 / 查询参数）.
     */
    @ExceptionHandler(BindException.class)
    public IResult<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数绑定失败");
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 处理缺少请求体异常.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public IResult<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体缺失或不可读: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "请求体不能为空");
    }

    /**
     * 处理 Spring ResponseStatusException，按异常自身状态码原样返回.
     *
     * <p>例如控制器主动抛出的 {@code 404 NOT_FOUND} 应直接透传 HTTP 状态码，
     * 而不是被下面的 {@code Exception} 兜底处理器包装成 200 body。</p>
     */
    @ExceptionHandler(ResponseStatusException.class)
    public void handleResponseStatusException(ResponseStatusException e, HttpServletResponse response)
            throws IOException {
        log.warn("响应状态异常: {} - {}", e.getStatusCode(), e.getReason());
        response.sendError(e.getStatusCode().value(), e.getReason());
    }

    /**
     * 处理其他未知异常.
     *
     * <p>默认对外返回异常自身 message；当 {@code me.exception.mask-unknown-message=true}
     * 时收敛为通用文案（"系统错误"），真实异常信息只进服务端日志，避免未知异常
     * 携带的 SQL、类路径、内网地址等内部细节泄漏给调用方。</p>
     */
    @ExceptionHandler(Exception.class)
    public IResult<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        String message = exceptionProperties.isMaskUnknownMessage()
                ? ResultCode.ERROR.getMsg()
                : e.getMessage();
        return errorResult(ResultCode.ERROR.getCode(), message, e);
    }

    private IResult<Void> errorResult(Integer code, String message, Throwable throwable) {
        if (exceptionProperties.isIncludeStacktrace()) {
            return Result.error(code, message, throwable);
        }
        return Result.error(code, message);
    }
}

package com.frame.me.base.advice;

import com.frame.me.api.result.IResult;
import com.frame.me.base.config.ExceptionProperties;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public IResult<Void> handleResponseStatusException(ResponseStatusException e, HttpServletResponse response) {
        log.warn("响应状态异常: {} - {}", e.getStatusCode(), e.getReason());
        response.setStatus(e.getStatusCode().value());
        return Result.error(e.getStatusCode().value(), e.getReason());
    }

    /**
     * 处理其他未知异常.
     *
     * <p>Spring Framework 7 起，MVC 请求侧异常族（{@code NoResourceFoundException} /
     * {@code HttpRequestMethodNotSupportedException} 等）不再继承 {@code ResponseStatusException}，
     * 仅实现 {@link ErrorResponse} 契约，会落入本兜底；此类异常自带客户端错误状态码
     * （404/405/415...）与请求级 message（无内部细节），按状态码原样透传，
     * 不适用未知异常的 500 与掩码语义。</p>
     *
     * <p>其余未知异常：默认对外返回通用文案（"系统错误"），真实异常信息只进服务端日志，
     * 避免 SQL、类路径、内网地址等内部细节泄漏；{@code me.exception.mask-unknown-message=false}
     * 时对外返回异常自身 message。</p>
     *
     * <p>日志级别：{@code NoResourceFoundException}（404，多为 DevTools .map 探测等客户端自发请求）
     * 降级 DEBUG，其余请求侧异常 WARN。</p>
     */
    @ExceptionHandler(Exception.class)
    public IResult<Void> handleException(Exception e, HttpServletResponse response) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            if (e instanceof NoResourceFoundException) {
                // 客户端请求了不存在的路径（高频是 DevTools 的 .map 探测），404 已透传，服务端无可动作，不刷 WARN
                log.debug("静态资源不存在: {}", e.getMessage());
            } else {
                log.warn("请求异常: {} - {}", statusCode, e.getMessage());
            }
            response.setStatus(statusCode.value());
            return Result.error(statusCode.value(), e.getMessage());
        }
        log.error("系统异常: {}", e.getMessage(), e);
        String message = exceptionProperties.isMaskUnknownMessage()
                ? ResultCode.ERROR.getMsg()
                : (e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        return errorResult(ResultCode.ERROR.getCode(), message, e);
    }

    private IResult<Void> errorResult(Integer code, String message, Throwable throwable) {
        if (exceptionProperties.isIncludeStacktrace()) {
            return Result.error(code, message, throwable);
        }
        return Result.error(code, message);
    }
}

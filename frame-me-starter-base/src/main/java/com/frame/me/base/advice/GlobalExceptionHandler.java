package com.frame.me.base.advice;

import com.frame.me.api.result.IResult;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常.
     */
    @ExceptionHandler(BusinessException.class)
    public IResult<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        return Result.error(e.getCode(), e.getMessage(), e);
    }

    /**
     * 处理内部异常.
     */
    @ExceptionHandler(InternalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public IResult<Void> handleInternalException(InternalException e) {
        log.error("内部异常: {}", e.getMessage(), e);
        return Result.error(e.getCode(), e.getMessage(), e);
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
     * 处理其他未知异常.
     */
    @ExceptionHandler(Exception.class)
    public IResult<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return Result.error(ResultCode.ERROR.getCode(), e.getMessage(), e);
    }
}

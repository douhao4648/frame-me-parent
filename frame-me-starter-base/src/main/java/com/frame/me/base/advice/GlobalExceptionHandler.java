package com.frame.me.base.advice;

import com.frame.me.api.result.IResult;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
        log.warn("业务异常: {}", e.getMessage(), e);
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
     * 处理其他未知异常.
     */
    @ExceptionHandler(Exception.class)
    public IResult<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return Result.error(ResultCode.ERROR.getCode(), e.getMessage(), e);
    }
}

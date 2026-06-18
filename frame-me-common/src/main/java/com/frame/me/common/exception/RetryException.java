package com.frame.me.common.exception;

import cn.hutool.core.util.StrUtil;
import com.frame.me.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class RetryException extends RuntimeException {

    private final Integer code;

    public RetryException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public RetryException(String message, Object... args) {
        super(StrUtil.format(message, args));
        this.code = ResultCode.ERROR.getCode();
    }

    public RetryException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public RetryException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public RetryException(ResultCode resultCode, String message, Object... args) {
        super(StrUtil.format(message, args));
        this.code = resultCode.getCode();
    }
}

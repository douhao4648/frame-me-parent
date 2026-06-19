package com.frame.me.base.exception;

import cn.hutool.core.util.StrUtil;
import com.frame.me.base.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(String message, Object... args) {
        super(StrUtil.format(message, args));
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message, Object... args) {
        super(StrUtil.format(message, args));
        this.code = resultCode.getCode();
    }
}

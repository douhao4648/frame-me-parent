package com.frame.me.common.exception;

import cn.hutool.core.util.StrUtil;
import com.frame.me.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class InternalException extends RuntimeException {

    private final Integer code;

    public InternalException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public InternalException(String message, Object... args) {
        super(StrUtil.format(message, args));
        this.code = ResultCode.ERROR.getCode();
    }

    public InternalException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public InternalException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public InternalException(ResultCode resultCode, String message, Object... args) {
        super(StrUtil.format(message, args));
        this.code = resultCode.getCode();
    }
}

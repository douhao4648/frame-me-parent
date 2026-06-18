package com.frame.me.common.result;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;
    private String err;
    private String rid;

    public static <T> Result<T> success(T data) {
        return of(ResultCode.SUCCESS, data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String message, Object... args) {
        return new Result<>(ResultCode.ERROR.getCode(), StrUtil.format(message, args), null, null, null);
    }

    public static <T> Result<T> error(Integer code, String message, String err) {
        return new Result<>(code, message, null, err, null);
    }

    public static <T> Result<T> error(Integer code, String message, Throwable err) {
        return new Result<>(code, message, null, ExceptionUtil.stacktraceToString(err), null);
    }

    public static <T> Result<T> error(ResultCode resultCode, Object... args) {
        return new Result<>(resultCode.getCode(), StrUtil.format(resultCode.getMsg(), args), null, null, null);
    }

    public static <T> Result<T> error(ResultCode resultCode, String message, Object... args) {
        return new Result<>(resultCode.getCode(), StrUtil.format(message, args), null, null, null);
    }

    public static <T> Result<T> of(ResultCode resultCode, T data) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), data, null, null);
    }
}

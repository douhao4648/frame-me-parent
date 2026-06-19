package com.frame.me.base.result;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import com.frame.me.api.result.IResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link IResult} 的默认实现，并提供静态工厂方法.
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements IResult<T> {

    private Integer code;
    private String msg;
    private T data;
    private String err;
    private String rid;

    /**
     * 构造成功响应.
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> IResult<T> success(T data) {
        return of(ResultCode.SUCCESS, data);
    }

    /**
     * 构造成功响应（无数据）.
     *
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> IResult<T> success() {
        return success(null);
    }

    /**
     * 构造错误响应.
     *
     * @param message 错误消息模板
     * @param args    模板参数
     * @param <T>     业务数据类型
     * @return 错误响应
     */
    public static <T> IResult<T> error(String message, Object... args) {
        return new Result<>(ResultCode.ERROR.getCode(), StrUtil.format(message, args), null, null, null);
    }

    /**
     * 构造错误响应.
     *
     * @param code    状态码
     * @param message 错误消息
     * @param err     错误详情
     * @param <T>     业务数据类型
     * @return 错误响应
     */
    public static <T> IResult<T> error(Integer code, String message, String err) {
        return new Result<>(code, message, null, err, null);
    }

    /**
     * 构造错误响应.
     *
     * @param code    状态码
     * @param message 错误消息
     * @param err     异常对象
     * @param <T>     业务数据类型
     * @return 错误响应
     */
    public static <T> IResult<T> error(Integer code, String message, Throwable err) {
        return new Result<>(code, message, null, ExceptionUtil.stacktraceToString(err), null);
    }

    /**
     * 构造错误响应.
     *
     * @param resultCode 状态码枚举
     * @param args       模板参数
     * @param <T>        业务数据类型
     * @return 错误响应
     */
    public static <T> IResult<T> error(ResultCode resultCode, Object... args) {
        return new Result<>(resultCode.getCode(), StrUtil.format(resultCode.getMsg(), args), null, null, null);
    }

    /**
     * 构造错误响应.
     *
     * @param resultCode 状态码枚举
     * @param message    错误消息模板
     * @param args       模板参数
     * @param <T>        业务数据类型
     * @return 错误响应
     */
    public static <T> IResult<T> error(ResultCode resultCode, String message, Object... args) {
        return new Result<>(resultCode.getCode(), StrUtil.format(message, args), null, null, null);
    }

    /**
     * 构造响应.
     *
     * @param resultCode 状态码枚举
     * @param data       业务数据
     * @param <T>        业务数据类型
     * @return 响应
     */
    public static <T> IResult<T> of(ResultCode resultCode, T data) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), data, null, null);
    }
}

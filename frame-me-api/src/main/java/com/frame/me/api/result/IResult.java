package com.frame.me.api.result;

/**
 * 统一响应结果接口.
 *
 * @param <T> 业务数据类型
 */
public interface IResult<T> {

    /**
     * 获取状态码.
     *
     * @return 状态码
     */
    Integer getCode();

    /**
     * 获取提示信息.
     *
     * @return 提示信息
     */
    String getMsg();

    /**
     * 获取业务数据.
     *
     * @return 业务数据
     */
    T getData();

    /**
     * 获取错误详情.
     *
     * @return 错误详情
     */
    String getErr();

    /**
     * 获取请求 ID.
     *
     * @return 请求 ID
     */
    String getRid();
}

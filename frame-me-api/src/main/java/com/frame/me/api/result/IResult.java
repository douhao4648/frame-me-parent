package com.frame.me.api.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应结果接口.
 *
 * @param <T> 业务数据类型
 */
@Schema(description = "统一响应结果")
public interface IResult<T> {

    /**
     * 获取状态码.
     *
     * @return 状态码
     */
    @Schema(description = "状态码")
    Integer getCode();

    /**
     * 获取提示信息.
     *
     * @return 提示信息
     */
    @Schema(description = "提示信息")
    String getMsg();

    /**
     * 获取业务数据.
     *
     * @return 业务数据
     */
    @Schema(description = "业务数据")
    T getData();

    /**
     * 获取错误详情.
     *
     * @return 错误详情
     */
    @Schema(description = "错误详情")
    String getErr();

    /**
     * 获取请求 ID.
     *
     * @return 请求 ID
     */
    @Schema(description = "请求 ID")
    String getRid();

    /**
     * 判断请求是否成功.
     *
     * <p>状态码为 200 时返回 {@code true}。</p>
     *
     * @return 成功返回 {@code true}，否则返回 {@code false}
     */
    @Schema(description = "是否成功", accessMode = Schema.AccessMode.READ_ONLY)
    default boolean isSuccess() {
        return getCode() != null && getCode() == 200;
    }
}

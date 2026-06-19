package com.frame.me.api.enums;

/**
 * 统一枚举接口.
 *
 * @param <T> 枚举 code 类型
 */
public interface IEnum<T> {

    /**
     * 获取枚举编码.
     *
     * @return 编码值
     */
    T getCode();

    /**
     * 获取枚举描述.
     *
     * @return 描述文本
     */
    String getDesc();
}

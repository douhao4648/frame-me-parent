package com.frame.me.api.enums;

import lombok.Getter;

/**
 * 性别枚举.
 */
@Getter
public enum GenderEnum implements IEnum<Integer> {

    /**
     * 未知.
     */
    UNKNOWN(0, "未知"),

    /**
     * 男.
     */
    MALE(1, "男"),

    /**
     * 女.
     */
    FEMALE(2, "女");

    private final Integer code;
    private final String desc;

    GenderEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

package com.frame.me.api.enums;

import lombok.Getter;

/**
 * 是/否枚举.
 */
@Getter
public enum YesNoEnum implements IEnum<Integer> {

    /**
     * 否.
     */
    NO(0, "否"),

    /**
     * 是.
     */
    YES(1, "是");

    private final Integer code;
    private final String desc;

    YesNoEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

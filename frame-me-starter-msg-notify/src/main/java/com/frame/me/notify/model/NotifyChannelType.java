package com.frame.me.notify.model;

/**
 * 通知通道类型.
 */
public enum NotifyChannelType {

    /**
     * 邮件.
     */
    EMAIL("email"),

    /**
     * 短信.
     */
    SMS("sms"),

    /**
     * 钉钉.
     */
    DINGTALK("dingtalk"),

    /**
     * 飞书.
     */
    FEISHU("feishu"),

    /**
     * 企业微信.
     */
    WECHAT_WORK("wechat-work");

    private final String code;

    NotifyChannelType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

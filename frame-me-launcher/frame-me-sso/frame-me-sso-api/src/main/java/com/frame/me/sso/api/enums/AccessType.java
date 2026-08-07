package com.frame.me.sso.api.enums;

/**
 * 应用接入类型.
 *
 * @author frame-me
 */
public enum AccessType {

    /** 集群内应用（登记关系标识，同样配密钥）. */
    INTERNAL,

    /** 集群外三方应用，强制密钥校验. */
    EXTERNAL
}

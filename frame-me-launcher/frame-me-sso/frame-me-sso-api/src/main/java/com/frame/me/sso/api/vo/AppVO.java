package com.frame.me.sso.api.vo;

import lombok.Data;

/**
 * 应用信息 VO.
 *
 * @author frame-me
 */
@Data
public class AppVO {

    private String appId;
    private String appName;
    private String accessType;
    /** 仅注册/重置时返回明文，否则 null. */
    private String appSecret;
    private String status;
}

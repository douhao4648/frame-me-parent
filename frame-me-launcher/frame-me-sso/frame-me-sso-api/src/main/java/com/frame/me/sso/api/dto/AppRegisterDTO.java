package com.frame.me.sso.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 注册应用请求 DTO.
 *
 * @author frame-me
 */
@Data
public class AppRegisterDTO {

    private String appName;

    /** INTERNAL / EXTERNAL. */
    private String accessType;

    private List<String> redirectUris;

    private String scopes;
}

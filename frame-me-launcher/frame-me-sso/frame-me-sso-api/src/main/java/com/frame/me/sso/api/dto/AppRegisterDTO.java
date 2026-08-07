package com.frame.me.sso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 注册应用请求 DTO.
 *
 * @author frame-me
 */
@Data
public class AppRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 应用名称. */
    @NotBlank(message = "appName 不能为空")
    private String appName;

    /** INTERNAL / EXTERNAL；垃圾值 400（否则 valueOf 抛 500）. */
    @NotBlank(message = "accessType 不能为空")
    @Pattern(regexp = "INTERNAL|EXTERNAL", message = "accessType 仅支持 INTERNAL / EXTERNAL")
    private String accessType;

    /** 回调白名单；为空注册后 authorize 永远 400，等于废应用. */
    @NotEmpty(message = "redirectUris 不能为空")
    private List<@NotBlank String> redirectUris;

    /** 授权范围，空格/逗号分隔；空默认 openid. */
    private String scopes;
}

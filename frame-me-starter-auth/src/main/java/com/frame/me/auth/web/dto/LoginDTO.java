package com.frame.me.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO（JWT / Sa-Token 等认证实现共用）.
 *
 * @author frame-me
 */
@Data
public class LoginDTO {

    /**
     * 登录账号.
     */
    @NotBlank(message = "账号不能为空")
    private String account;

    /**
     * 登录密码.
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}

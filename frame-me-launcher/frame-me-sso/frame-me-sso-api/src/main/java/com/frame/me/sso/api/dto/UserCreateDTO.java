package com.frame.me.sso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建用户请求 DTO.
 *
 * @author frame-me
 */
@Data
public class UserCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录账号，唯一. */
    @NotBlank(message = "account 不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]{2,64}$", message = "account 仅支持字母/数字/_.-，长度 2-64")
    private String account;

    /** 初始密码（明文提交，服务端 BCrypt 加密入库）. */
    @NotBlank(message = "password 不能为空")
    @Size(min = 6, max = 64, message = "password 长度须 6-64")
    private String password;

    /** 用户名. */
    @NotBlank(message = "name 不能为空")
    private String name;

    /** 角色码，逗号分隔；空表示无角色. */
    private String roles;
}

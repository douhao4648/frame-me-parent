package com.frame.me.sso.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户请求 DTO：所有字段 null 表示不更新.
 *
 * <p>password 一并在此更新（服务端 BCrypt 加密）；改密码或置 DISABLED 会
 * 联动踢出该用户全部会话（即时生效，不等 token 自然过期）.</p>
 *
 * @author frame-me
 */
@Data
public class UserUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名；null 不更新. */
    private String name;

    /** 新密码（明文，服务端 BCrypt 加密）；null 不更新. */
    @Size(min = 6, max = 64, message = "password 长度须 6-64")
    private String password;

    /** 角色码，逗号分隔；null 不更新. */
    private String roles;

    /** ACTIVE / DISABLED；null 不更新，垃圾值 400. */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 仅支持 ACTIVE / DISABLED")
    private String status;
}

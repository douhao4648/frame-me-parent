package com.frame.me.sso.api.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户信息响应 VO（/userinfo 端点返回）.
 *
 * @author frame-me
 */
@Data
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID. */
    private String sub;

    /** 登录账号. */
    private String account;

    /** 用户名. */
    private String name;

    /** 角色码，逗号分隔. */
    private String roles;
}

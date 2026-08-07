package com.frame.me.sso.api.vo;

import lombok.Data;

/**
 * 用户信息 VO（管理端用；永不含密码字段）.
 *
 * @author frame-me
 */
@Data
public class UserVO {

    private Long id;
    private String account;
    private String name;
    private String status;
    private String roles;
}

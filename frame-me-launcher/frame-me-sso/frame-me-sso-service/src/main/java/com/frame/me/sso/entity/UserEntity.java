package com.frame.me.sso.entity;

import com.frame.me.mybatis.flex.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SSO 用户实体.
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sso_user")
public class UserEntity extends BaseEntity {

    /** 登录账号，唯一. */
    @Column
    private String account;

    /** BCrypt 加密密码. */
    @Column
    private String password;

    /** 用户名. */
    @Column
    private String name;

    /** 状态：ACTIVE / DISABLED. */
    @Column
    private String status;

    /** 角色码，逗号分隔（sa-token StpInterface 读）. */
    @Column
    private String roles;
}

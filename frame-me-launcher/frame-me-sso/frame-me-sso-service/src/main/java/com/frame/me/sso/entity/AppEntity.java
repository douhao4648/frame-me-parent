package com.frame.me.sso.entity;

import com.frame.me.mybatis.flex.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SSO 应用注册表实体.
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sso_app")
public class AppEntity extends BaseEntity {

    /** 应用标识，SSO 分配. */
    @Column
    private String appId;

    /** 应用名称. */
    @Column
    private String appName;

    /** 接入类型：INTERNAL / EXTERNAL. */
    @Column
    private String accessType;

    /** 密钥；INTERNAL 为 null，EXTERNAL 存加密后密钥. */
    @Column
    private String appSecret;

    /** 回调白名单（JSON 数组）. */
    @Column
    private String redirectUris;

    /** 授权范围，逗号分隔. */
    @Column
    private String scopes;

    /** 状态：ACTIVE / DISABLED. */
    @Column
    private String status;

    /** 注册/重置时返回明文用，不入库. */
    @Column(ignore = true)
    private transient String appSecretPlain;
}

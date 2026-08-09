package com.frame.me.base.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户公共基类.
 *
 * <p><strong>注意：此类不是数据库实体</strong>，仅抽取各业务模块通用的用户字段，
 * 不依赖 MyBatis-Plus 注解，也不对应任何数据库表。
 *
 * <p>具体的 adapter / 业务模块可继承此类，再补充各自专属字段。
 */
@Data
public class User implements Serializable {

    /**
     * 状态：启用.
     */
    public static final Integer STATUS_ENABLED = 1;
    /**
     * 状态：禁用.
     */
    public static final Integer STATUS_DISABLED = 0;
    private static final long serialVersionUID = 1L;
    /**
     * 用户 ID.
     */
    private Long id;

    /**
     * 登录账号 / 用户名.
     */
    private String account;

    /**
     * 登录密码（建议存储加密后的密文）.
     *
     * <p>排除在 {@code toString()} 之外：口令哈希落入日志会抬高离线爆破面，
     * 打印 user 对象排查问题时不得带出本字段。
     *
     * <p>{@code WRITE_ONLY}：Jackson 只允许反序列化写入（用户创建/改密请求体），
     * 任何序列化输出（{@code /user} 等接口响应、sa-token 会话缓存 JSON）一律剥离本字段，
     * 口令哈希不出服务端。
     */
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 用户编号（工号、会员号等，业务侧可自由定义）.
     */
    private String userNo;

    /**
     * 昵称 / 显示名.
     */
    private String nickname;

    /**
     * 真实姓名.
     */
    private String realName;

    /**
     * 手机号.
     */
    private String mobile;

    /**
     * 邮箱.
     */
    private String email;

    /**
     * 头像 URL.
     */
    private String avatar;

    /**
     * 性别：0 未知 / 1 男 / 2 女.
     */
    private Integer gender;

    /**
     * 出生日期.
     */
    private LocalDate birthday;

    /**
     * 账号状态：0 禁用 / 1 启用，默认启用.
     */
    private Integer status = STATUS_ENABLED;

    /**
     * 用户类型：例如 1 超级管理员 / 2 管理员 / 3 普通用户.
     *
     * <p>具体取值由各业务模块自行约定。
     */
    private Integer userType;

    /**
     * 注册来源 / 注册渠道（如 pc、app、wechat、admin 等）.
     */
    private String registerSource;

    /**
     * 创建时间.
     */
    private LocalDateTime createTime;

    /**
     * 更新时间.
     */
    private LocalDateTime updateTime;

    /**
     * 最近一次登录时间.
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最近一次登录 IP.
     */
    private String lastLoginIp;

    /**
     * 备注.
     */
    private String remark;

    /**
     * 租户 ID（多租户场景下使用，单租户业务可忽略）.
     */
    private Long tenantId;

    /**
     * 部门 ID（组织架构场景下使用，非必须）.
     */
    private Long deptId;

    /**
     * 机构 / 组织 ID（组织架构场景下使用，非必须）.
     */
    private Long orgId;

}

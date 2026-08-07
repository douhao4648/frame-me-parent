package com.frame.me.sso.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新应用请求 DTO.
 *
 * <p>承载可更新字段（回调白名单、scope、状态）。appId 由路径传入，不在 body.
 * 全部字段可空（空表示不更新），非空时做格式校验.</p>
 *
 * @author frame-me
 */
@Data
public class AppUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回调白名单；null 表示不更新，非空时至少一项（空列表会让应用变废：authorize 永远 400）. */
    @Size(min = 1, message = "redirectUris 至少一项，不更新请传 null")
    private List<String> redirectUris;

    /** 授权范围，逗号分隔. */
    private String scopes;

    /** 状态：ACTIVE / DISABLED；null 表示不更新，垃圾值直接 400（防写脏数据静默禁用）. */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 仅支持 ACTIVE / DISABLED")
    private String status;
}

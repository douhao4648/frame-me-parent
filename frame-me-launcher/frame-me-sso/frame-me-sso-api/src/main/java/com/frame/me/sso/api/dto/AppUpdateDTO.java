package com.frame.me.sso.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新应用请求 DTO.
 *
 * <p>承载可更新字段（回调白名单、scope、状态）。appId 由路径传入，不在 body.</p>
 *
 * @author frame-me
 */
@Data
public class AppUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回调白名单. */
    private List<String> redirectUris;

    /** 授权范围，逗号分隔. */
    private String scopes;

    /** 状态：ACTIVE / DISABLED. */
    private String status;
}

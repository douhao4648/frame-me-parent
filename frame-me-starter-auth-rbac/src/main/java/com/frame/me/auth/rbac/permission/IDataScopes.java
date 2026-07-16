package com.frame.me.auth.rbac.permission;

/**
 * 数据范围常量接口.
 *
 * @author frame-me
 */
public interface IDataScopes {

    /**
     * 全部数据.
     */
    String ALL = "ALL";

    /**
     * 本部门数据.
     */
    String DEPT = "DEPT";

    /**
     * 本机构数据.
     */
    String ORG = "ORG";

    /**
     * 本人数据.
     */
    String SELF = "SELF";

    /**
     * 自定义数据（{@code dataIds} 逐条授予）.
     */
    String CUSTOM = "CUSTOM";
}

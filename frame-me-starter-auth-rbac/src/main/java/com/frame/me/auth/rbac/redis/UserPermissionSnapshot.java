package com.frame.me.auth.rbac.redis;

import com.frame.me.auth.rbac.permission.Permission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户权限快照，用于在 Redis 中缓存某用户的角色与权限.
 *
 * @author frame-me
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色标识集合.
     */
    private Set<String> roles = new HashSet<>();

    /**
     * 资源/操作权限列表.
     */
    private List<Permission> permissions = new ArrayList<>();
}

package com.frame.me.auth.rbac.permission;

import com.frame.me.base.user.User;

import java.util.Collection;
import java.util.Collections;

/**
 * 认证权限提供者 SPI.
 *
 * <p>业务可以通过实现本接口，从数据库、缓存或远程服务加载当前用户的角色、权限和数据权限。
 * 默认实现 {@link ConfigAuthPermissionProvider} 从配置文件中读取映射关系。</p>
 *
 * @author frame-me
 */
public interface IAuthPermissionProvider {

    /**
     * 获取用户拥有的角色标识列表.
     *
     * @param user 当前用户
     * @return 角色标识集合，无角色时返回空集合
     */
    default Collection<String> getRoles(User user) {
        return Collections.emptyList();
    }

    /**
     * 获取用户拥有的资源/操作权限列表.
     *
     * @param user 当前用户
     * @return 权限集合，无权限时返回空集合
     */
    default Collection<Permission> getPermissions(User user) {
        return Collections.emptyList();
    }

    /**
     * 获取用户拥有的数据权限列表.
     *
     * <p>框架在请求级一次性批量加载并缓存（同角色/权限），供 SpEL 单条校验与
     * Service 层 Helper 使用。默认返回空集合（不限制数据范围）；
     * 数据库等自定义实现的热路径建议搭配 Redis 后端缓存。</p>
     *
     * @param user 当前用户
     * @return 数据权限集合，无数据权限时返回空集合
     */
    default Collection<DataPermission> getDataPermissions(User user) {
        return Collections.emptyList();
    }
}

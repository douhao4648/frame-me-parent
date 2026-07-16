package com.frame.me.auth.rbac.permission;

import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;

import java.util.Set;

/**
 * 数据权限静态入口（业务 Service 层使用）.
 *
 * <p>读取当前请求线程已加载的数据权限（由 Filter / Interceptor 触发
 * {@link AuthPermissionHolder#ensureLoaded} 加载），业务据此显式拼接查询条件。</p>
 *
 * <p>各 scope 的判定路径（内核只收「可机判」形态，行级比对归业务 SQL）:
 * <ul>
 * <li>{@code ALL} → {@link #isAll} 短路放行（{@code check}/{@code checkOwner} 同样短路）;</li>
 * <li>{@code CUSTOM} → {@link #dataIds} 并集 / {@link #check}(id IN 语义);ids 只来自自定义
 * provider（配置版条目 ids 恒空）,scope 为 CUSTOM 是 provider 契约标记，判定本身不滤 scope;</li>
 * <li>{@code SELF} → 单条 {@link #checkOwner}（查行比对 owner);列表业务拼 {@code created_by = 当前用户};</li>
 * <li>{@code DEPT}/{@code ORG} → 单条 {@link #checkDept}/{@link #checkOrg}(查行比对);
 * 列表经 {@link #scopes} 判别后业务拼 {@code dept_id/org_id} 条件。</li>
 * </ul>
 *
 * @author frame-me
 */
public class AuthDataPermissions {

    private AuthDataPermissions() {
    }

    /**
     * 当前用户是否拥有指定资源的 {@code ALL} 数据范围.
     */
    public static boolean isAll(String resource) {
        return isAll(resource, null);
    }

    /**
     * 当前用户是否拥有指定资源与操作的 {@code ALL} 数据范围.
     */
    public static boolean isAll(String resource, String action) {
        return DataPermissionResolver.isAll(AuthPermissionHolder.getDataPermissions(), resource, action);
    }

    /**
     * 当前用户对指定资源的合并 scope 并集.
     */
    public static Set<String> scopes(String resource) {
        return scopes(resource, null);
    }

    /**
     * 当前用户对指定资源与操作的合并 scope 并集.
     */
    public static Set<String> scopes(String resource, String action) {
        return DataPermissionResolver.scopes(AuthPermissionHolder.getDataPermissions(), resource, action);
    }

    /**
     * 当前用户对指定资源的合并数据 ID 并集（资源行主键集合）.
     */
    public static Set<Long> dataIds(String resource) {
        return dataIds(resource, null);
    }

    /**
     * 当前用户对指定资源与操作的合并数据 ID 并集（资源行主键集合）.
     */
    public static Set<Long> dataIds(String resource, String action) {
        return DataPermissionResolver.dataIds(AuthPermissionHolder.getDataPermissions(), resource, action);
    }

    /**
     * 校验当前用户是否可访问指定资源的某条数据（命中 {@code ALL} 或数据 ID 并集）.
     *
     * @param dataId 数据行主键，支持 {@link Number} 或数字字符串
     */
    public static boolean check(String resource, Object dataId) {
        return check(resource, null, dataId);
    }

    /**
     * 校验当前用户是否可访问指定资源与操作的某条数据（命中 {@code ALL} 或数据 ID 并集）.
     *
     * @param dataId 数据行主键，支持 {@link Number} 或数字字符串
     */
    public static boolean check(String resource, String action, Object dataId) {
        return DataPermissionResolver.check(AuthPermissionHolder.getDataPermissions(), resource, action,
                DataPermissionResolver.toLong(dataId));
    }

    /**
     * 校验单条数据的归属（编辑/删除等单条操作的 {@code SELF} 场景）.
     *
     * <p>命中 {@code ALL} 放行；命中 {@code SELF} 且 {@code ownerId} 等于当前用户放行；否则拒绝。
     * 行归属在行数据里而不在权限快照里，需先查出该行再调用本方法
     * （编辑/删除接口本来就要查行，无额外成本），数据量大时也不需枚举 dataIds。</p>
     *
     * @param resource 资源标识
     * @param ownerId  数据行的归属用户 ID（如 {@code createdBy})
     * @return 是否可操作
     */
    public static boolean checkOwner(String resource, Long ownerId) {
        return checkOwner(resource, null, ownerId);
    }

    /**
     * 校验单条数据的归属（带操作）.
     *
     * @param resource 资源标识
     * @param action   操作标识
     * @param ownerId  数据行的归属用户 ID（如 {@code createdBy})
     * @return 是否可操作
     */
    public static boolean checkOwner(String resource, String action, Long ownerId) {
        return checkScopeField(resource, action, IDataScopes.SELF, ownerId, AuthContext.getUserId());
    }

    /**
     * 校验单条数据的部门归属（编辑/删除等单条操作的 {@code DEPT} 场景）.
     *
     * <p>命中 {@code ALL} 放行；命中 {@code DEPT} 且 {@code deptId} 等于当前用户部门放行；否则拒绝。
     * 同 {@link #checkOwner}，需先查出该行再调用。</p>
     *
     * @param resource 资源标识
     * @param deptId   数据行的归属部门 ID
     * @return 是否可操作
     */
    public static boolean checkDept(String resource, Long deptId) {
        return checkDept(resource, null, deptId);
    }

    /**
     * 校验单条数据的部门归属（带操作）.
     *
     * @param resource 资源标识
     * @param action   操作标识
     * @param deptId   数据行的归属部门 ID
     * @return 是否可操作
     */
    public static boolean checkDept(String resource, String action, Long deptId) {
        User user = AuthContext.getUser();
        return checkScopeField(resource, action, IDataScopes.DEPT, deptId,
                user == null ? null : user.getDeptId());
    }

    /**
     * 校验单条数据的机构归属（编辑/删除等单条操作的 {@code ORG} 场景）.
     *
     * <p>命中 {@code ALL} 放行；命中 {@code ORG} 且 {@code orgId} 等于当前用户机构放行；否则拒绝。
     * 同 {@link #checkOwner}，需先查出该行再调用。</p>
     *
     * @param resource 资源标识
     * @param orgId    数据行的归属机构 ID
     * @return 是否可操作
     */
    public static boolean checkOrg(String resource, Long orgId) {
        return checkOrg(resource, null, orgId);
    }

    /**
     * 校验单条数据的机构归属（带操作）.
     *
     * @param resource 资源标识
     * @param action   操作标识
     * @param orgId    数据行的归属机构 ID
     * @return 是否可操作
     */
    public static boolean checkOrg(String resource, String action, Long orgId) {
        User user = AuthContext.getUser();
        return checkScopeField(resource, action, IDataScopes.ORG, orgId,
                user == null ? null : user.getOrgId());
    }

    /**
     * 「用户字段 vs 行字段」归属判定：{@code ALL} 短路放行；命中指定 scope 且
     * 行归属值等于用户对应字段放行；其余（含未登录、用户字段或行值为 {@code null}）拒绝.
     */
    private static boolean checkScopeField(String resource, String action, String scope,
                                           Long rowValue, Long userValue) {
        if (isAll(resource, action)) {
            return true;
        }
        return rowValue != null && userValue != null
                && scopes(resource, action).contains(scope)
                && rowValue.equals(userValue);
    }
}

package com.frame.me.auth.satoken.permission;

import cn.dev33.satoken.stp.StpInterface;
import com.frame.me.auth.satoken.config.SaTokenAuthProperties;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置版 sa-token 权限数据源.
 *
 * <p>从 {@code me.auth.sa-token.users}（用户 → 角色）与 {@code me.auth.sa-token.roles}
 * （角色 → 权限码）读取权限数据；权限码（{@code resource:action} 或 {@code resource}）
 * 原样透传给 sa-token，不做解析。配置缺失时返回空列表。</p>
 *
 * <p>业务声明任意 {@link StpInterface} Bean 即接管（本默认实现以
 * {@code @ConditionalOnMissingBean} 退避）；接入数据库的实现应自行做缓存——
 * sa-token 官方明确权限数据缓存是实现方责任，每次鉴权都会回调本接口。
 * 本实现的两份配置启动后不变，构造期一次性预解析为 List，运行期仅 map 查找。</p>
 *
 * @author frame-me
 */
public class ConfigStpInterface implements StpInterface {

    /**
     * 用户 ID → 预解析角色列表.
     */
    private final Map<String, List<String>> userRoles;

    /**
     * 角色 → 预解析权限码列表.
     */
    private final Map<String, List<String>> rolePermissions;

    public ConfigStpInterface(SaTokenAuthProperties properties) {
        this.userRoles = presplit(properties.getUsers());
        this.rolePermissions = presplit(properties.getRoles());
    }

    /**
     * 返回指定账号拥有的角色标识列表.
     *
     * @param loginId  登录 ID（本框架为用户 ID）
     * @param loginType 账号类型（未使用）
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return List.of();
        }
        return userRoles.getOrDefault(String.valueOf(loginId), List.of());
    }

    /**
     * 返回指定账号拥有的权限码列表（按角色展开，权限码原样透传）.
     *
     * @param loginId  登录 ID（本框架为用户 ID）
     * @param loginType 账号类型（未使用）
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return getRoleList(loginId, loginType).stream()
                .flatMap(role -> rolePermissions.getOrDefault(role, List.<String>of()).stream())
                .distinct()
                .toList();
    }

    /**
     * 启动期一次性把 CSV 配置预解析为去重 List，空白段剔除.
     */
    private static Map<String, List<String>> presplit(Map<String, String> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, csv) -> result.put(key, splitCsv(csv)));
        return result;
    }

    /**
     * 逗号分隔串拆分为去重列表，空白段剔除.
     */
    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}

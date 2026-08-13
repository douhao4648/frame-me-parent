package com.frame.me.sso.service;

import com.frame.me.sso.api.enums.AccessType;
import com.frame.me.sso.entity.AppEntity;

import java.util.List;

/**
 * SSO 应用注册服务接口.
 *
 * <p>INTERNAL / EXTERNAL 均生成并加密存储 secret（明文仅注册/重置时返回一次），
 * 换 token（含 client_credentials）一律强制校验密钥.</p>
 *
 * @author frame-me
 */
public interface IAppService {

    /**
     * 注册应用.
     *
     * @return 新建的 app；appSecretPlain 为明文（仅此一次返回），appSecret 为加密后
     */
    AppEntity register(String appName, AccessType accessType, List<String> redirectUris, String scopes);

    /**
     * 根据 appId 查询应用.
     */
    AppEntity findByAppId(String appId);

    /**
     * 查询全部应用.
     */
    List<AppEntity> list();

    /**
     * 更新应用.
     */
    void update(AppEntity app);

    /**
     * 禁用应用.
     */
    void disable(String appId);

    /**
     * 重置应用密钥.
     *
     * @return 新明文密钥
     */
    String resetSecret(String appId);

    /**
     * 校验应用密钥（INTERNAL / EXTERNAL 均强制）.
     */
    boolean verifySecret(AppEntity app, String inputSecret);

    /**
     * 校验回调地址是否在白名单.
     */
    boolean isRedirectAllowed(AppEntity app, String redirectUri);

    /**
     * 校验请求的 scope 是否 ⊆ 应用注册的 scopes.
     */
    boolean isScopeAllowed(AppEntity app, String scope);
}

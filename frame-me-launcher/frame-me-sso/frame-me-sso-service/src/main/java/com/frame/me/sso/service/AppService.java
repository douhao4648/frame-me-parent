package com.frame.me.sso.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson2.JSON;
import com.frame.me.sso.entity.AppEntity;
import com.frame.me.sso.api.enums.AccessType;
import com.frame.me.sso.api.enums.AppStatus;
import com.frame.me.sso.mapper.AppMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SSO 应用注册服务.
 *
 * <p>INTERNAL 不发 secret（免 aksk），EXTERNAL 生成并加密存储 secret。</p>
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppService {

    private final AppMapper appMapper;

    /**
     * 注册应用.
     *
     * @return 新建的 app；EXTERNAL 时 appSecretPlain 为明文（仅此一次返回），appSecret 为加密后
     */
    public AppEntity register(String appName, AccessType accessType, List<String> redirectUris,
                           String scopes) {
        AppEntity app = new AppEntity();
        app.setAppId(generateAppId(accessType));
        app.setAppName(appName);
        app.setAccessType(accessType.name());
        app.setRedirectUris(JSON.toJSONString(redirectUris));
        app.setScopes(scopes);
        app.setStatus(AppStatus.ACTIVE.name());

        if (accessType == AccessType.EXTERNAL) {
            String plainSecret = generateSecret();
            app.setAppSecret(encryptSecret(plainSecret));
            app.setAppSecretPlain(plainSecret);
        }
        // INTERNAL: app_secret 保持 null
        appMapper.insert(app);
        return app;
    }

    /**
     * 根据 appId 查询应用.
     */
    public AppEntity findByAppId(String appId) {
        return appMapper.selectOneByQuery(QueryWrapper.create().eq("app_id", appId));
    }

    /**
     * 查询全部应用.
     */
    public List<AppEntity> list() {
        return appMapper.selectListByQuery(QueryWrapper.create());
    }

    /**
     * 更新应用.
     */
    public void update(AppEntity app) {
        appMapper.update(app);
    }

    /**
     * 禁用应用.
     */
    public void disable(String appId) {
        AppEntity app = findByAppId(appId);
        if (app != null) {
            app.setStatus(AppStatus.DISABLED.name());
            appMapper.update(app);
        }
    }

    /**
     * 重置 EXTERNAL 应用密钥.
     *
     * @return 新明文密钥
     */
    public String resetSecret(String appId) {
        AppEntity app = findByAppId(appId);
        if (app == null || !AccessType.EXTERNAL.name().equals(app.getAccessType())) {
            throw new IllegalArgumentException("仅 EXTERNAL 应用可重置密钥");
        }
        String plainSecret = generateSecret();
        app.setAppSecret(encryptSecret(plainSecret));
        appMapper.update(app);
        return plainSecret;
    }

    /**
     * 校验应用密钥（INTERNAL 免验）.
     */
    public boolean verifySecret(AppEntity app, String inputSecret) {
        if (app.getAccessType().equals(AccessType.INTERNAL.name())) {
            return true;
        }
        if (inputSecret == null || inputSecret.isBlank()) {
            return false;
        }
        return encryptSecret(inputSecret).equals(app.getAppSecret());
    }

    /**
     * 校验回调地址是否在白名单.
     */
    public boolean isRedirectAllowed(AppEntity app, String redirectUri) {
        if (app.getRedirectUris() == null || app.getRedirectUris().isBlank()) {
            return false;
        }
        List<String> uris = JSON.parseArray(app.getRedirectUris(), String.class);
        return uris.contains(redirectUri);
    }

    private String generateAppId(AccessType accessType) {
        return "fm-" + accessType.name().toLowerCase() + "-" + IdUtil.fastSimpleUUID().substring(0, 8);
    }

    private String generateSecret() {
        return IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
    }

    /**
     * ponytail: 现期 SHA256 单向哈希比对；生产可走 sensi-encrypt 对称加密（可还原明文）。
     * 升级路径：替换为 StringEncryptor 加密存储，verifySecret 用 StringEncryptor 解密后比对。
     */
    private String encryptSecret(String plain) {
        return SecureUtil.sha256(plain);
    }
}

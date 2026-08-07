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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SSO 应用注册服务.
 *
 * <p>INTERNAL / EXTERNAL 均生成并加密存储 secret（明文仅注册/重置时返回一次），
 * 换 token（含 client_credentials）一律强制校验密钥.</p>
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
     * @return 新建的 app；appSecretPlain 为明文（仅此一次返回），appSecret 为加密后
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

        // INTERNAL / EXTERNAL 均发 secret：换 token 强制验密钥，appId 不再是凭证
        String plainSecret = generateSecret();
        app.setAppSecret(encryptSecret(plainSecret));
        app.setAppSecretPlain(plainSecret);
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
     * 重置应用密钥.
     *
     * @return 新明文密钥
     */
    public String resetSecret(String appId) {
        AppEntity app = findByAppId(appId);
        if (app == null) {
            throw new IllegalArgumentException("应用不存在");
        }
        String plainSecret = generateSecret();
        app.setAppSecret(encryptSecret(plainSecret));
        appMapper.update(app);
        return plainSecret;
    }

    /**
     * 校验应用密钥（INTERNAL / EXTERNAL 均强制）.
     */
    public boolean verifySecret(AppEntity app, String inputSecret) {
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

    /**
     * 校验请求的 scope 是否 ⊆ 应用注册的 scopes.
     *
     * <p>请求的 scope 是用户输入（OAuth 惯例空格分隔，兼容逗号），不校验会被当成
     * 任意字符串写进授权码；注册侧 scopes 同样按空格/逗号拆分比对.</p>
     */
    public boolean isScopeAllowed(AppEntity app, String scope) {
        if (scope == null || scope.isBlank()) {
            // 未请求 scope（取默认授权），放行
            return true;
        }
        Set<String> registered = splitScopes(app.getScopes());
        Set<String> requested = splitScopes(scope);
        return !requested.isEmpty() && registered.containsAll(requested);
    }

    private Set<String> splitScopes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[\\s,]+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String generateAppId(AccessType accessType) {
        return accessType.name().toLowerCase() + "-" + IdUtil.fastSimpleUUID().substring(0, 8);
    }

    private String generateSecret() {
        return IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
    }

    /**
     * SHA256 单向哈希比对：secret 只验不还原（明文仅注册/重置时返回一次），
     * 与口令同策，不需要可逆加密。secret 为 64 位随机串，熵足够，无需加盐慢哈希。
     */
    private String encryptSecret(String plain) {
        return SecureUtil.sha256(plain);
    }
}

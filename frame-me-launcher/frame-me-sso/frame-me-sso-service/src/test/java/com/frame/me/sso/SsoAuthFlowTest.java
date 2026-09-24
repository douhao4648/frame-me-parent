package com.frame.me.sso;

import com.frame.me.sso.api.enums.AccessType;
import com.frame.me.sso.entity.AppEntity;
import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.service.IAppService;
import com.frame.me.sso.service.IUserService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSO 授权码全流程端到端测试.
 *
 * <p>Testcontainers 起 Redis（授权码存储 + 事件桥接），H2 存用户/应用，
 * TestRestTemplate 走真实 HTTP（静态资源、Filter 链全部生效），覆盖：</p>
 * <ul>
 *   <li>登录页可匿名访问（AuthFilter whitelist 对静态资源生效）</li>
 *   <li>未登录 authorize → 302 登录页，回跳链不丢 state</li>
 *   <li>登录 → authorize 发 code（state 原样回显）→ 换 token → Bearer 调 /userinfo</li>
 *   <li>授权码一次性（重放 4001 凭证错误）、scope 越权 400、redirectUri 非白名单 400（均 HTTP 200 + body 码）</li>
 *   <li>EXTERNAL 应用换 token 强制校验 appSecret</li>
 *   <li>用户 CRUD：创建/列表/详情/更新/删除，重复账号与垃圾入参拒绝，防自锁，
 *       改密码/禁用/删除联动踢会话，管理端点设备闸拦截应用 token</li>
 * </ul>
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class SsoAuthFlowTest {

    private static final String CALLBACK = "http://client.local/cb";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IUserService userService;

    @Autowired
    private IAppService appService;

    private AppEntity internalApp;

    @BeforeAll
    static void dockerAvailable() {
        boolean available;
        try {
            DockerClientFactory.instance().client();
            available = true;
        } catch (Exception e) {
            available = false;
        }
        Assumptions.assumeTrue(available, "Docker 不可用，跳过 SSO 端到端测试");
    }

    @BeforeEach
    void seedData() {
        // H2 跨用例共享（DB_CLOSE_DELAY=-1），uk_account 唯一约束下幂等种子
        if (userService.findByAccount("alice") == null) {
            UserEntity user = new UserEntity();
            user.setAccount("alice");
            user.setPassword(new BCryptPasswordEncoder().encode("123456"));
            user.setName("Alice");
            user.setStatus("ACTIVE");
            user.setRoles("admin,user");
            userService.save(user);
        }

        internalApp = appService.register("内部应用", AccessType.INTERNAL,
                List.of(CALLBACK), "openid profile");
    }

    /**
     * P0 回归：登录页静态资源未登录可访问（AuthFilter whitelist 放行）.
     */
    @Test
    void loginPageAccessibleAnonymous() {
        ResponseEntity<String> res = rest.getForEntity("/sso-login.html", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("SSO 统一登录");
    }

    /**
     * {@code @LoginUser} 参数经元注解 {@code @Parameter(hidden = true)} 从 OpenAPI 文档忽略：
     * /base/auth/user 的 user 入参由服务端从认证上下文解析，不应出现在文档参数里.
     *
     * <p>springdoc 由 {@code -Pswagger} profile 引入，默认 profile 下 api-docs 404，此时跳过.</p>
     */
    @Test
    void loginUserParameterHiddenFromApiDocs() {
        ResponseEntity<String> res = rest.getForEntity("/v3/api-docs/base-api", String.class);
        Assumptions.assumeTrue(res.getStatusCode() == HttpStatus.OK,
                "springdoc 未启用（需 -Pswagger），跳过 api-docs 断言");
        assertThat(res.getBody()).contains("/base/auth/user");
        assertThat(res.getBody()).doesNotContain("\"name\":\"user\"");
    }

    /**
     * 未登录 authorize → 302 登录页，回跳地址携带原始参数（含 state，逐值编码）.
     */
    @Test
    void authorizeRedirectsToLoginPageWhenAnonymous() {
        ResponseEntity<Void> res = noRedirect().getForEntity(authorizeUrl(internalApp.getAppId(), "openid", "xyz123"), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = res.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).startsWith("/sso-login.html?redirect=");
        // 内层 authorize URL 整体编码，解码后应完整携带 state（登录页跳转链不丢）
        String inner = location.substring("/sso-login.html?redirect=".length());
        String decoded = java.net.URLDecoder.decode(inner, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded).startsWith("/api/auth/authorize?")
                .contains("appId=" + internalApp.getAppId())
                .contains("state=xyz123");
    }

    /**
     * 全流程：登录 → authorize 发 code（state 回显）→ 换 token → Bearer /userinfo；
     * 授权码重放被拒（GETDEL 原子消费）.
     */
    @Test
    @SuppressWarnings("unchecked")
    void fullFlowLoginCodeTokenUserinfo() {
        String satoken = login("alice", "123456");

        // authorize（带登录态 + state）→ 302 回调含 code 与 state 回显
        ResponseEntity<Void> authRes = noRedirect().exchange(
                authorizeUrl(internalApp.getAppId(), "openid", "xyz123"),
                HttpMethod.GET, new HttpEntity<>(satokenHeaders(satoken)), Void.class);
        assertThat(authRes.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        URI callback = URI.create(authRes.getHeaders().getFirst(HttpHeaders.LOCATION));
        assertThat(callback.toString()).startsWith(CALLBACK + "?code=");
        var params = UriComponentsBuilder.fromUri(callback).build().getQueryParams();
        String code = params.getFirst("code");
        assertThat(code).isNotBlank();
        assertThat(params.getFirst("state")).isEqualTo("xyz123");

        // code 换 token（INTERNAL 同样强制 secret）
        Map<String, Object> tokenBody = postForMap("/api/auth/token", Map.of(
                "code", code,
                "appId", internalApp.getAppId(),
                "appSecret", internalApp.getAppSecretPlain(),
                "redirectUri", CALLBACK));
        assertThat(tokenBody.get("code")).isEqualTo(200);
        String accessToken = (String) ((Map<String, Object>) tokenBody.get("data")).get("accessToken");
        assertThat(accessToken).isNotBlank();

        // 授权码重放 → 4001 凭证错误（GETDEL 已消费）
        Map<String, Object> replay = postForMap("/api/auth/token", Map.of(
                "code", code,
                "appId", internalApp.getAppId(),
                "appSecret", internalApp.getAppSecretPlain(),
                "redirectUri", CALLBACK));
        assertThat(replay.get("code")).isEqualTo(4001);

        // Bearer 调 /userinfo（@Anonymous 放行 AuthFilter，端点自验 token）→ 200 + 用户信息
        HttpHeaders bearer = new HttpHeaders();
        bearer.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        ResponseEntity<Map> infoRes = rest.exchange("/api/users/info",
                HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
        assertThat(infoRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> info = infoRes.getBody();
        assertThat(info.get("code")).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) info.get("data");
        assertThat(data.get("account")).isEqualTo("alice");
        assertThat(data.get("name")).isEqualTo("Alice");
    }

    /**
     * /userinfo 对无效 token 返 401（body code）——HTTP 200 + body 401 说明请求
     * 到达了端点而非被 AuthFilter 拦截（AuthFilter 拦截是 HTTP 401）.
     */
    @Test
    @SuppressWarnings("unchecked")
    void userinfoRejectsInvalidToken() {
        HttpHeaders bearer = new HttpHeaders();
        bearer.set(HttpHeaders.AUTHORIZATION, "Bearer garbage-token");
        ResponseEntity<Map> res = rest.exchange("/api/users/info",
                HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("code")).isEqualTo(401);
    }

    /**
     * scope 越权（请求 admin 不在应用注册的 openid profile 内）→ 400 业务码 + 原因 message（HTTP 200）.
     */
    @Test
    void scopeOutsideWhitelistRejected() {
        ResponseEntity<String> res = rest.getForEntity(
                authorizeUrl(internalApp.getAppId(), "openid admin", null), String.class);
        // BusinessException(BAD_REQUEST) → HTTP 200 + body code=400（此前用 ResponseStatusException 直出 HTTP 400，破坏"恒 200"契约）
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("scope 超出应用授权范围");
    }

    /**
     * redirectUri 不在白名单 → 400 业务码 + 原因 message（禁止重定向回跳，RFC 6749 §4.1.2.1；HTTP 200）.
     */
    @Test
    void redirectUriNotWhitelistedRejected() {
        String url = "/api/auth/authorize?appId=" + internalApp.getAppId()
                + "&redirectUri=http://evil.com/cb&scope=openid";
        ResponseEntity<String> res = rest.getForEntity(url, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("redirectUri 不在应用白名单");
    }

    /**
     * EXTERNAL 应用换 token 强制校验 appSecret：缺失/错误 401，正确 200.
     */
    @Test
    void externalAppRequiresSecret() {
        AppEntity externalApp = appService.register("外部应用", AccessType.EXTERNAL,
                List.of(CALLBACK), "openid");
        String secret = externalApp.getAppSecretPlain();
        assertThat(secret).isNotBlank();

        String code = issueCode(externalApp.getAppId(), login("alice", "123456"));

        // 缺 secret → 4001 凭证错误
        assertThat(postForMap("/api/auth/token", Map.of(
                "code", code, "appId", externalApp.getAppId(), "redirectUri", CALLBACK))
                .get("code")).isEqualTo(4001);

        // 错 secret → 4001 凭证错误
        String code2 = issueCode(externalApp.getAppId(), login("alice", "123456"));
        assertThat(postForMap("/api/auth/token", Map.of(
                "code", code2, "appId", externalApp.getAppId(),
                "appSecret", "wrong", "redirectUri", CALLBACK))
                .get("code")).isEqualTo(4001);

        // 正确 secret → 200
        String code3 = issueCode(externalApp.getAppId(), login("alice", "123456"));
        assertThat(postForMap("/api/auth/token", Map.of(
                "code", code3, "appId", externalApp.getAppId(),
                "appSecret", secret, "redirectUri", CALLBACK))
                .get("code")).isEqualTo(200);
    }

    /**
     * Boot 4 的 TestRestTemplate 默认跟随重定向，断言 302 的调用统一走不跟随的副本.
     */
    private TestRestTemplate noRedirect() {
        return rest.withRedirects(HttpRedirects.DONT_FOLLOW);
    }

    /**
     * updateApp 校验：垃圾 status 拒绝（body code 400）且不写库；合法枚举值正常更新.
     */
    @Test
    @SuppressWarnings("unchecked")
    void updateAppRejectsGarbageStatus() {
        String satoken = login("alice", "123456");
        String appId = internalApp.getAppId();

        Map<String, Object> bad = postForMap("/api/apps/" + appId,
                Map.of("status", "garbage"), satoken);
        assertThat(bad.get("code")).isEqualTo(400);
        assertThat(appService.findByAppId(appId).getStatus()).isEqualTo("ACTIVE");

        // 空回调白名单 → 400（会把应用变废，null 才是"不更新"）
        Map<String, Object> emptyUris = postForMap("/api/apps/" + appId,
                Map.of("redirectUris", List.of()), satoken);
        assertThat(emptyUris.get("code")).isEqualTo(400);

        Map<String, Object> ok = postForMap("/api/apps/" + appId,
                Map.of("status", "DISABLED"), satoken);
        assertThat(ok.get("code")).isEqualTo(200);
        assertThat(appService.findByAppId(appId).getStatus()).isEqualTo("DISABLED");
    }

    /**
     * registerApp 校验：垃圾 accessType / 空 appName / 空回调白名单 拒绝（body code 400）且不落库；
     * 合法注册正常返回 appId.
     */
    @Test
    void registerAppRejectsInvalidPayload() {
        String satoken = login("alice", "123456");
        int before = appService.list().size();

        // 垃圾 accessType → 400（否则 AccessType.valueOf 抛 500）
        Map<String, Object> badType = postForMap("/api/apps/",
                Map.of("appName", "x", "accessType", "garbage",
                        "redirectUris", List.of(CALLBACK)), satoken);
        assertThat(badType.get("code")).isEqualTo(400);

        // 空 appName → 400（Map.of 不支持 null 值，空格触发 NotBlank）
        Map<String, Object> blankName = postForMap("/api/apps/",
                Map.of("appName", " ", "accessType", "INTERNAL",
                        "redirectUris", List.of(CALLBACK)), satoken);
        assertThat(blankName.get("code")).isEqualTo(400);

        // 空回调白名单 → 400（注册后 authorize 永远失败的废应用）
        Map<String, Object> noCallback = postForMap("/api/apps/",
                Map.of("appName", "x", "accessType", "INTERNAL",
                        "redirectUris", List.of()), satoken);
        assertThat(noCallback.get("code")).isEqualTo(400);

        assertThat(appService.list()).hasSize(before);

        // 合法注册 → 200 + appId
        Map<String, Object> ok = postForMap("/api/apps/",
                Map.of("appName", "合法应用", "accessType", "INTERNAL",
                        "redirectUris", List.of(CALLBACK)), satoken);
        assertThat(ok.get("code")).isEqualTo(200);
    }

    /**
     * client_credentials 模式：appId+appSecret 直换应用 token（无用户维度），
     * 错 secret 401、未知 grantType 400、应用 token 调 /userinfo 401.
     */
    @Test
    @SuppressWarnings("unchecked")
    void clientCredentialsIssuesAppToken() {
        AppEntity externalApp = appService.register("定时任务应用", AccessType.EXTERNAL,
                List.of(CALLBACK), "openid");
        String secret = externalApp.getAppSecretPlain();

        // 缺 secret → 4001 凭证错误
        assertThat(postForMap("/api/auth/token", Map.of(
                "grantType", "client_credentials", "appId", externalApp.getAppId()))
                .get("code")).isEqualTo(4001);

        // 错 secret → 4001 凭证错误
        assertThat(postForMap("/api/auth/token", Map.of(
                "grantType", "client_credentials", "appId", externalApp.getAppId(),
                "appSecret", "wrong"))
                .get("code")).isEqualTo(4001);

        // 正确 secret → 200 + token
        Map<String, Object> ok = postForMap("/api/auth/token", Map.of(
                "grantType", "client_credentials", "appId", externalApp.getAppId(),
                "appSecret", secret));
        assertThat(ok.get("code")).isEqualTo(200);
        String appToken = (String) ((Map<String, Object>) ok.get("data")).get("accessToken");
        assertThat(appToken).isNotBlank();

        // 未知 grantType → 400
        assertThat(postForMap("/api/auth/token", Map.of(
                "grantType", "password", "appId", externalApp.getAppId()))
                .get("code")).isEqualTo(400);

        // INTERNAL 应用同样支持 client_credentials（强制 secret，注册即发密钥）
        Map<String, Object> internalOk = postForMap("/api/auth/token", Map.of(
                "grantType", "client_credentials", "appId", internalApp.getAppId(),
                "appSecret", internalApp.getAppSecretPlain()));
        assertThat(internalOk.get("code")).isEqualTo(200);

        // 应用 token 调 /userinfo → 401（无用户维度，fail-closed 而非 500）
        HttpHeaders bearer = new HttpHeaders();
        bearer.set(HttpHeaders.AUTHORIZATION, "Bearer " + appToken);
        ResponseEntity<Map> infoRes = rest.exchange("/api/users/info",
                HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
        assertThat(infoRes.getBody().get("code")).isEqualTo(401);
    }

    /**
     * 按 appId 踢人：该应用的用户 token + client_credentials 应用 token 全部失效，
     * 其他应用的会话不受影响.
     */
    @Test
    @SuppressWarnings("unchecked")
    void logoutAppKicksAllSessionsOfApp() {
        // 被踢 app 用 EXTERNAL（client_credentials 仅限 EXTERNAL）；internalApp 用户 token 作对照组
        AppEntity kickedApp = appService.register("被踢应用", AccessType.EXTERNAL,
                List.of(CALLBACK), "openid");
        String secret = kickedApp.getAppSecretPlain();

        String userToken = exchangeUserToken(kickedApp);
        String otherUserToken = exchangeUserToken(internalApp);
        Map<String, Object> cc = postForMap("/api/auth/token", Map.of(
                "grantType", "client_credentials", "appId", kickedApp.getAppId(),
                "appSecret", secret));
        String appToken = (String) ((Map<String, Object>) cc.get("data")).get("accessToken");

        // 管理端按 appId 踢（admin 会话调用）
        String admin = login("alice", "123456");
        Map<String, Object> kick = postForMap("/api/apps/" + kickedApp.getAppId() + "/logout",
                Map.of(), admin);
        assertThat(kick.get("code")).isEqualTo(200);

        // 被踢 app 的用户 token 与应用 token 均失效
        assertThat(userinfoCode(userToken)).isEqualTo(401);
        assertThat(userinfoCode(appToken)).isEqualTo(401);
        // 其他 app 的用户 token 不受影响
        assertThat(userinfoCode(otherUserToken)).isEqualTo(200);
    }

    /**
     * 管理端点设备闸：SSO 下发的用户 token（deviceType=appId）放入 satoken 头
     * 调管理端点 → 403；浏览器登录会话（deviceType=DEF）正常放行.
     */
    @Test
    void adminEndpointsRejectAppScopedToken() {
        String appScopedToken = exchangeUserToken(internalApp);

        // 应用 token 塞 satoken 头调管理端点 → 403（BrowserDeviceInterceptor）
        Map<String, Object> blocked = postForMap("/api/apps/",
                Map.of("appName", "x", "accessType", "INTERNAL",
                        "redirectUris", List.of(CALLBACK)), appScopedToken);
        assertThat(blocked.get("code")).isEqualTo(403);

        // 浏览器登录会话 → 放行（角色 admin + deviceType=DEF）
        String browserToken = login("alice", "123456");
        Map<String, Object> ok = postForMap("/api/apps/",
                Map.of("appName", "浏览器会话应用", "accessType", "INTERNAL",
                        "redirectUris", List.of(CALLBACK)), browserToken);
        assertThat(ok.get("code")).isEqualTo(200);
    }

    /**
     * 全局登出（用户触发）：清 SSO 会话 + 全部应用 token（/userinfo 立即 401）；
     * 应用 token（client_credentials，loginId="app:"+appId）调用 → 403.
     */
    @Test
    @SuppressWarnings("unchecked")
    void globalLogoutKicksAllSessionsAndRejectsAppToken() {
        String browserToken = login("alice", "123456");
        String userToken = exchangeUserToken(internalApp);
        assertThat(userinfoCode(userToken)).isEqualTo(200);

        // 应用 token（loginId="app:"+appId）无用户维度 → AuthFilter 解析不到用户 → 401
        Map<String, Object> cc = postForMap("/api/auth/token", Map.of(
                "grantType", "client_credentials", "appId", internalApp.getAppId(),
                "appSecret", internalApp.getAppSecretPlain()));
        String appToken = (String) ((Map<String, Object>) cc.get("data")).get("accessToken");
        ResponseEntity<Map> rejected = rest.exchange("/api/auth/logout",
                HttpMethod.POST, new HttpEntity<>(satokenHeaders(appToken)), Map.class);
        boolean appRejected = rejected.getStatusCode().value() == 401
                || (rejected.getBody() != null
                        && Integer.valueOf(401).equals(rejected.getBody().get("code")));
        assertThat(appRejected).as("应用 token 调全局登出应 401（无用户维度）").isTrue();

        // 浏览器会话触发全局登出 → 200，SSO 会话与该用户全部应用 token 同时失效
        assertThat(postForMap("/api/auth/logout", Map.of(), browserToken).get("code")).isEqualTo(200);
        assertThat(userinfoCode(userToken)).isEqualTo(401);
        ResponseEntity<Map> afterLogout = rest.exchange("/api/apps/",
                HttpMethod.POST, new HttpEntity<>(satokenHeaders(browserToken)), Map.class);
        boolean unauthorized = afterLogout.getStatusCode().value() == 401
                || (afterLogout.getBody() != null
                        && Integer.valueOf(401).equals(afterLogout.getBody().get("code")));
        assertThat(unauthorized).as("全局登出后浏览器会话应失效").isTrue();
    }

    /**
     * 禁用应用即生效：disable 联动踢出存量会话，已颁发 token 立即 401.
     */
    @Test
    void disableAppRevokesIssuedTokens() {
        AppEntity app = appService.register("待禁用应用", AccessType.INTERNAL,
                List.of(CALLBACK), "openid");
        String userToken = exchangeUserToken(app);
        assertThat(userinfoCode(userToken)).isEqualTo(200);

        String admin = login("alice", "123456");
        Map<String, Object> res = postForMap("/api/apps/" + app.getAppId() + "/disable",
                Map.of(), admin);
        assertThat(res.get("code")).isEqualTo(200);

        // 禁用后存量 token 立即失效（不再等自然过期）
        assertThat(userinfoCode(userToken)).isEqualTo(401);
    }

    /**
     * 禁用旁路回归：经普通更新接口（PUT/POST update）把状态置为 DISABLED，
     * 与专用禁用接口同一安全语义——联动踢出该应用全部存量会话，已颁发 token 立即 401.
     */
    @Test
    void updateAppDisableAlsoRevokesIssuedTokens() {
        AppEntity app = appService.register("普通更新禁用应用", AccessType.INTERNAL,
                List.of(CALLBACK), "openid");
        String userToken = exchangeUserToken(app);
        assertThat(userinfoCode(userToken)).isEqualTo(200);

        String admin = login("alice", "123456");
        Map<String, Object> res = postForMap("/api/apps/" + app.getAppId(),
                Map.of("status", "DISABLED"), admin);
        assertThat(res.get("code")).isEqualTo(200);

        // 禁用后存量 token 立即失效（不再等自然过期）
        assertThat(userinfoCode(userToken)).isEqualTo(401);
    }

    /**
     * 用户 CRUD 全生命周期：创建（VO 无密码字段）→ 重复账号拒绝 → 详情/列表 →
     * 更新 name/roles → 删除（逻辑删 + 踢会话）→ 详情报错、账号不可再登录.
     */
    @Test
    @SuppressWarnings("unchecked")
    void userCrudLifecycle() {
        String admin = login("alice", "123456");

        // 创建 → 200 + VO（永不含 password 字段）
        Map<String, Object> created = postForMap("/api/users/", Map.of(
                "account", "bob_crud", "password", "123456", "name", "Bob", "roles", "user"), admin);
        assertThat(created.get("code")).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) created.get("data");
        assertThat(data.get("account")).isEqualTo("bob_crud");
        assertThat(data).doesNotContainKey("password");
        long bobId = ((Number) data.get("id")).longValue();

        // 重复账号 → 错误且不落库
        Map<String, Object> dup = postForMap("/api/users/", Map.of(
                "account", "bob_crud", "password", "123456", "name", "Bob2"), admin);
        assertThat(dup.get("code")).isNotEqualTo(200);
        assertThat(userService.list().stream()
                .filter(u -> "bob_crud".equals(u.getAccount())).count()).isEqualTo(1);

        // 垃圾 account → 400（@Pattern）
        assertThat(postForMap("/api/users/", Map.of(
                "account", "bad account!", "password", "123456", "name", "X"), admin)
                .get("code")).isEqualTo(400);

        // 详情 + 列表包含新用户
        ResponseEntity<Map> detail = rest.exchange("/api/users/" + bobId,
                HttpMethod.GET, new HttpEntity<>(satokenHeaders(admin)), Map.class);
        assertThat(((Map<String, Object>) detail.getBody().get("data")).get("name")).isEqualTo("Bob");
        ResponseEntity<Map> list = rest.exchange("/api/users/",
                HttpMethod.GET, new HttpEntity<>(satokenHeaders(admin)), Map.class);
        List<Map<String, Object>> users = (List<Map<String, Object>>) list.getBody().get("data");
        assertThat(users).anyMatch(u -> "bob_crud".equals(u.get("account")));

        // 更新 name/roles（null 字段不更新）
        Map<String, Object> updated = postForMap("/api/users/" + bobId,
                Map.of("name", "Bobby", "roles", "user,ops"), admin);
        assertThat(updated.get("code")).isEqualTo(200);
        assertThat(userService.findById(bobId).getName()).isEqualTo("Bobby");
        assertThat(userService.findById(bobId).getRoles()).isEqualTo("user,ops");

        // 新用户可登录；删除后登录失败、详情报错
        assertThat(postForMap("/base/auth/login", Map.of(
                "account", "bob_crud", "password", "123456")).get("code")).isEqualTo(200);
        ResponseEntity<Map> del = rest.exchange("/api/users/" + bobId,
                HttpMethod.DELETE, new HttpEntity<>(satokenHeaders(admin)), Map.class);
        assertThat(del.getBody().get("code")).isEqualTo(200);
        assertThat(postForMap("/base/auth/login", Map.of(
                "account", "bob_crud", "password", "123456")).get("code")).isNotEqualTo(200);
        ResponseEntity<Map> gone = rest.exchange("/api/users/" + bobId,
                HttpMethod.GET, new HttpEntity<>(satokenHeaders(admin)), Map.class);
        assertThat(gone.getBody().get("code")).isNotEqualTo(200);
    }

    /**
     * updateUser 校验与防自锁：垃圾 status / 短密码 400；禁用当前登录账号拒绝；
     * 禁用其他用户即时生效（登录被拒 + 存量会话被踢）.
     */
    @Test
    void updateUserValidationAndSelfGuard() {
        String admin = login("alice", "123456");
        Long aliceId = userService.findByAccount("alice").getId();

        // 垃圾 status → 400（@Pattern，否则写库成非法状态）
        assertThat(postForMap("/api/users/" + aliceId,
                Map.of("status", "garbage"), admin).get("code")).isEqualTo(400);
        // 短密码 → 400（@Size）
        assertThat(postForMap("/api/users/" + aliceId,
                Map.of("password", "123"), admin).get("code")).isEqualTo(400);

        // 禁用/删除当前登录账号 → 拒绝（防自锁）
        assertThat(postForMap("/api/users/" + aliceId,
                Map.of("status", "DISABLED"), admin).get("code")).isNotEqualTo(200);
        ResponseEntity<Map> delSelf = rest.exchange("/api/users/" + aliceId,
                HttpMethod.DELETE, new HttpEntity<>(satokenHeaders(admin)), Map.class);
        assertThat(delSelf.getBody().get("code")).isNotEqualTo(200);
        assertThat(userService.findByAccount("alice").getStatus()).isEqualTo("ACTIVE");

        // 禁用其他用户 → 200，该用户登录被拒
        postForMap("/api/users/", Map.of(
                "account", "carol_guard", "password", "123456", "name", "Carol"), admin);
        Long carolId = userService.findByAccount("carol_guard").getId();
        assertThat(postForMap("/api/users/" + carolId,
                Map.of("status", "DISABLED"), admin).get("code")).isEqualTo(200);
        assertThat(postForMap("/base/auth/login", Map.of(
                "account", "carol_guard", "password", "123456")).get("code")).isNotEqualTo(200);
    }

    /**
     * 改密码即时生效：旧密码登录失败、新密码可登录、存量会话被踢.
     */
    @Test
    void passwordChangeKicksSessionsAndRejectsOld() {
        String admin = login("alice", "123456");
        postForMap("/api/users/", Map.of(
                "account", "dave_pwd", "password", "123456", "name", "Dave"), admin);
        Long daveId = userService.findByAccount("dave_pwd").getId();
        String daveToken = login("dave_pwd", "123456");

        assertThat(postForMap("/api/users/" + daveId,
                Map.of("password", "newpass1"), admin).get("code")).isEqualTo(200);

        // 旧密码登录失败，新密码可登录
        assertThat(postForMap("/base/auth/login", Map.of(
                "account", "dave_pwd", "password", "123456")).get("code")).isNotEqualTo(200);
        assertThat(postForMap("/base/auth/login", Map.of(
                "account", "dave_pwd", "password", "newpass1")).get("code")).isEqualTo(200);

        // 改密码前建立的会话已被踢（sa-token 会话失效）
        ResponseEntity<Map> res = rest.exchange("/api/users/",
                HttpMethod.GET, new HttpEntity<>(satokenHeaders(daveToken)), Map.class);
        boolean unauthorized = res.getStatusCode().value() == 401
                || (res.getBody() != null && Integer.valueOf(401).equals(res.getBody().get("code")));
        assertThat(unauthorized).as("改密码后存量会话应被踢").isTrue();
    }

    /**
     * 用户管理端点同样受设备闸保护：应用 token 塞 satoken 头 → 403.
     */
    @Test
    void userManagementRejectsAppScopedToken() {
        String appScopedToken = exchangeUserToken(internalApp);
        Map<String, Object> blocked = postForMap("/api/users/", Map.of(
                "account", "eve_gate", "password", "123456", "name", "Eve"), appScopedToken);
        assertThat(blocked.get("code")).isEqualTo(403);
        assertThat(userService.findByAccount("eve_gate")).isNull();
    }

    /**
     * 登录 → authorize → code → token，拿某 app 的用户 token（带应用密钥）.
     */
    @SuppressWarnings("unchecked")
    private String exchangeUserToken(AppEntity app) {
        String code = issueCode(app.getAppId(), login("alice", "123456"));
        Map<String, Object> tokenBody = postForMap("/api/auth/token", Map.of(
                "code", code, "appId", app.getAppId(), "redirectUri", CALLBACK,
                "appSecret", app.getAppSecretPlain()));
        assertThat(tokenBody.get("code")).as("换 token 应成功").isEqualTo(200);
        return (String) ((Map<String, Object>) tokenBody.get("data")).get("accessToken");
    }

    /**
     * Bearer 调 /userinfo，返回 body 的 code.
     */
    private int userinfoCode(String token) {
        HttpHeaders bearer = new HttpHeaders();
        bearer.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        ResponseEntity<Map> res = rest.exchange("/api/users/info",
                HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
        return (int) res.getBody().get("code");
    }

    private String authorizeUrl(String appId, String scope, String state) {
        String url = "/api/auth/authorize?appId=" + appId
                + "&redirectUri=" + CALLBACK + "&scope=" + scope.replace(" ", "%20");
        return state != null ? url + "&state=" + state : url;
    }

    @SuppressWarnings("unchecked")
    private String login(String account, String password) {
        Map<String, Object> body = postForMap("/base/auth/login",
                Map.of("account", account, "password", password));
        assertThat(body.get("code")).as("登录应成功").isEqualTo(200);
        return (String) ((Map<String, Object>) body.get("data")).get("accessToken");
    }

    /**
     * 带登录态调 authorize 拿授权码（EXTERNAL 用例复用）.
     */
    private String issueCode(String appId, String satoken) {
        ResponseEntity<Void> res = noRedirect().exchange(authorizeUrl(appId, "openid", null),
                HttpMethod.GET, new HttpEntity<>(satokenHeaders(satoken)), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return UriComponentsBuilder.fromUriString(res.getHeaders().getFirst(HttpHeaders.LOCATION))
                .build().getQueryParams().getFirst("code");
    }

    private HttpHeaders satokenHeaders(String satoken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("satoken", satoken);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(String path, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
        return res.getBody();
    }

    /**
     * 带登录态的 POST（管理端点需要 admin 角色的会话）.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(String path, Map<String, ?> body, String satoken) {
        HttpHeaders headers = satokenHeaders(satoken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
        return res.getBody();
    }
}

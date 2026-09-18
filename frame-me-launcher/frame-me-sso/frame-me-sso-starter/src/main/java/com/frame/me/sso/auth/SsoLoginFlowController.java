package com.frame.me.sso.auth;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sso.config.SsoClientProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * SSO 登录流程控制器（RP 下游通用）：{@code /sso-authorize} 发起登录 +
 * 授权码回调两种落地方式.
 *
 * <p><b>方式 A：hash 回调</b>（SPA/Header 鉴权场景，默认 {@code GET /index}）：
 * {@code redirect-uri} 配为本端点。服务端从 Redis 消费一次性 state 后，将 code
 * 放入目标地址的 URL hash，目标页再调 {@code POST /sso-login} 换本地会话。</p>
 *
 * <p><b>方式 B：服务端换 token</b>（浏览器 Cookie 会话场景，默认 {@code GET /callback}）：
 * {@code redirect-uri} 配为本端点，服务端先从 Redis 消费一次性 state，再完成
 * code 换 token → 建本地会话（sa-token 下游 {@code loginByUser} 内部
 * {@code stpLogic.login} 在请求上下文中原生写 Cookie）→ 302 跳回登录前目标地址。
 * JWT 下游不签发 Cookie，
 * 不适用本端点，请走方式 A。</p>
 *
 * <p>两端点路径均可配（{@code me.sso.client.index-path} / {@code me.sso.client.callback-path}），
 * 改路径需同步改 {@code me.sso.client.redirect-uri} 与 SSO 应用注册的 redirectUri 白名单。</p>
 *
 * <p><b>为何不用 {@code static/} 静态资源</b>：静态 index.html 会把访问路径钉死在
 * {@code /index.html} 并劫持所有下游的 {@code /} welcome page（与下游自有静态资源按
 * classpath 顺序互踩），且静态资源不吃 {@code @Anonymous} 注解、需各下游手工配
 * whitelist——控制器换干净路径 + 匿名随 starter 分发，零配置。</p>
 *
 * <p><b>安全说明</b>：登录必须从 {@code /sso-authorize} 发起，由 RP 生成高熵 state
 * 写入 Redis 并以 Cookie nonce 绑定浏览器；两种回调都原子消费 state + 校验 nonce，
 * 拒绝伪造、过期和重放（集群多节点任意节点可消费，不依赖 HttpSession）。
 * 方式 B 的 code 经 query 进入服务端，
 * 会被访问日志（默认关闭）与浏览器历史记录——授权码一次性（GETDEL 原子消费）+ 60s
 * 过期 + 落地即消费，泄露窗口可接受。</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 回调", description = "SSO 授权码回调落地（hash 落地页 / 服务端 Cookie 会话）")
@Anonymous
@RestController
@RequiredArgsConstructor
public class SsoLoginFlowController {

    private static final Resource INDEX_HTML = new ClassPathResource("sso/index.html");
    /**
     * RFC 3986 unreserved + reserved + '%'（已有转义保留）.
     */
    private static final String URI_LEGAL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%";
    private final SsoAuthService ssoAuthService;
    private final SsoClientProperties properties;
    private final SsoStateStore stateStore;

    /**
     * 从 RP 发起 SSO 登录。state 在此生成并写入 Redis（Cookie nonce 绑定浏览器），调用方不能自行提供。
     */
    @Operation(summary = "发起 SSO 登录", description = "生成一次性 state 并重定向到 SSO authorize 端点")
    @GetMapping("${me.sso.client.authorize-path:/sso-authorize}")
    public ResponseEntity<Void> authorize(
            @RequestParam(required = false, defaultValue = "/") String target,
            @RequestParam(required = false, defaultValue = "openid") String scope,
            HttpServletResponse response) {
        requireConfigured(properties.getBaseUrl(), "me.sso.client.base-url");
        requireConfigured(properties.getAppId(), "me.sso.client.app-id");
        requireConfigured(properties.getRedirectUri(), "me.sso.client.redirect-uri");

        String state = stateStore.issue(response, target);
        URI location = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/auth/authorize")
                .queryParam("appId", properties.getAppId())
                .queryParam("redirectUri", properties.getRedirectUri())
                .queryParam("scope", scope)
                .queryParam("state", state)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /**
     * 方式 A：SSO 回调落地页（匿名，text/html）.
     *
     * @return index.html 页面资源
     */
    @Operation(summary = "SSO 回调落地页", description = "无授权码时展示静态落地页")
    @GetMapping(value = "${me.sso.client.index-path:/index}", params = "!code",
            produces = MediaType.TEXT_HTML_VALUE)
    public Resource index() {
        return INDEX_HTML;
    }

    /**
     * SPA/Header 模式回调：校验并消费 state 后，将 code 放入目标页 fragment。
     */
    @Operation(summary = "SSO SPA 回调", description = "校验一次性 state 后，经 hash 携带 code 跳转目标页")
    @GetMapping(value = "${me.sso.client.index-path:/index}", params = "code")
    public ResponseEntity<Void> indexCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        String target = stateStore.consume(request, response, state);
        int fragmentIndex = target.indexOf('#');
        String targetWithoutFragment = fragmentIndex >= 0 ? target.substring(0, fragmentIndex) : target;
        String location = targetWithoutFragment + "#code=" + encodeFragment(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(toUri(location)).build();
    }

    /**
     * 方式 B：SSO 授权码服务端回调：消费 state、用 code 换本地会话（写 Cookie）后
     * 302 回登录前保存在会话中的目标地址.
     *
     * @param code  SSO 授权码（一次性，60s 过期）
     * @param state RP 发起登录时签发的一次性随机值（Redis 存储 + Cookie nonce 绑定）
     * @return 302 重定向
     */
    @Operation(summary = "SSO 服务端回调", description = "校验 state、用 code 换本地会话后回到登录前地址（浏览器 Cookie 会话场景）")
    @GetMapping("${me.sso.client.callback-path:/callback}")
    public ResponseEntity<Void> callback(
            @Parameter(description = "SSO 授权码（一次性，60s 过期）") @RequestParam String code,
            @Parameter(description = "RP 签发的一次性 state") @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        String target = stateStore.consume(request, response, state);
        // 换 token 失败抛 4001（授权码无效/已用），全局异常处理返回错误 JSON，fail-closed 不回跳
        ssoAuthService.ssoLogin(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(toUri(target)).build();
    }

    private String encodeFragment(String value) {
        return UriComponentsBuilder.newInstance().fragment(value).build().encode().getFragment();
    }

    private void requireConfigured(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.ERROR, property + " 未配置");
        }
    }

    /**
     * 构造回跳 URI：合法字符与已有 {@code %XX} 转义原样保留，仅对非法字符
     * （如裸中文）按 UTF-8 percent-encode——{@code URI.create} 遇裸非 ASCII 直接抛异常，
     * {@code UriComponentsBuilder#encode} 又会把已有转义二次编码，故手工按需编码。
     */
    private URI toUri(String target) {
        StringBuilder sb = new StringBuilder(target.length() + 16);
        for (int i = 0; i < target.length(); ) {
            int cp = target.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x80 && URI_LEGAL.indexOf(cp) >= 0) {
                sb.append((char) cp);
            } else {
                for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
                            .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
                }
            }
        }
        return URI.create(sb.toString());
    }
}

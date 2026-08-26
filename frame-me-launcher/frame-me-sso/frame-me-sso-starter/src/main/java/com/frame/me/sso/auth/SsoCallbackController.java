package com.frame.me.sso.auth;

import com.frame.me.auth.annotation.Anonymous;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * SSO 授权码回调控制器（RP 下游通用），提供两种落地方式.
 *
 * <p><b>方式 A：hash 落地页</b>（SPA/Header 鉴权场景，默认 {@code GET /index}）：
 * {@code redirect-uri} 配为本端点（如 {@code http://your-app/index}），authorize 回调
 * 带 {@code code}/{@code state} 落在该页，页面 JS 校验 state（站内相对路径，否则回落
 * {@code /}）后经 URL hash 携带 code 重定向到目标地址，由目标页（SPA 路由）调
 * {@code POST /sso-login} 换本地会话。</p>
 *
 * <p><b>方式 B：服务端换 token</b>（浏览器 Cookie 会话场景，默认 {@code GET /callback}）：
 * {@code redirect-uri} 配为本端点，code/state 进服务端，由服务端完成 code 换 token →
 * 建本地会话（sa-token 下游 {@code loginByUser} 内部 {@code stpLogic.login} 在请求上下文中
 * 原生写 Cookie）→ 302 跳回 {@code state} 地址，前端零 JS。JWT 下游不签发 Cookie，
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
 * <p><b>安全说明</b>：两端点 state 校验同一套规则（仅站内相对路径，拒绝 {@code //}
 * 协议相对、反斜杠、空白），防 open redirect；方式 B 的 code 经 query 进入服务端，
 * 会被访问日志（默认关闭）与浏览器历史记录——授权码一次性（GETDEL 原子消费）+ 60s
 * 过期 + 落地即消费，泄露窗口可接受。</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 回调", description = "SSO 授权码回调落地（hash 落地页 / 服务端 Cookie 会话）")
@Anonymous
@RestController
@RequiredArgsConstructor
public class SsoCallbackController {

    private static final Resource INDEX_HTML = new ClassPathResource("sso/index.html");

    private final SsoAuthService ssoAuthService;

    /**
     * 方式 A：SSO 回调落地页（匿名，text/html）.
     *
     * @return index.html 页面资源
     */
    @Operation(summary = "SSO 回调落地页", description = "hash 落地页：页面 JS 校验 state 后经 hash 携带 code 跳转目标页，由目标页调 /sso-login 换本地会话（SPA/Header 鉴权场景）")
    @GetMapping(value = "${me.sso.client.index-path:/index}", produces = MediaType.TEXT_HTML_VALUE)
    public Resource index() {
        return INDEX_HTML;
    }

    /**
     * 方式 B：SSO 授权码服务端回调：code 换本地会话（写 Cookie）后 302 回 {@code state} 地址.
     *
     * @param code SSO 授权码（一次性，60s 过期）
     * @param state 登录前目标地址（仅接受站内相对路径，否则回落 {@code /}）
     * @return 302 重定向
     */
    @Operation(summary = "SSO 服务端回调", description = "code 换本地会话（sa-token 原生写 Cookie）后 302 回 state 地址，前端零 JS（浏览器 Cookie 会话场景，JWT 下游不适用）")
    @GetMapping("${me.sso.client.callback-path:/callback}")
    public ResponseEntity<Void> callback(
            @Parameter(description = "SSO 授权码（一次性，60s 过期）") @RequestParam String code,
            @Parameter(description = "登录前目标地址（仅站内相对路径，否则回落 /）") @RequestParam(required = false) String state) {
        // 换 token 失败抛 4001（授权码无效/已用），全局异常处理返回错误 JSON，fail-closed 不回跳
        ssoAuthService.ssoLogin(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(toUri(resolveTarget(state))).build();
    }

    /**
     * 校验并返回回跳目标：仅站内相对路径（与内置落地页 index.html 同一套规则），
     * 缺失/不合法回落 {@code /}。{@code #} 片段保留——方式 B 的 code 不经浏览器，
     * 片段无冲突，hash 路由 SPA 的 state（如 {@code /#/log/page}）依赖它定位目标页。
     */
    private String resolveTarget(String state) {
        if (state == null || !state.matches("^/(?!/)[^\\\\\\s]*$")) {
            return "/";
        }
        return state;
    }

    /**
     * 构造回跳 URI：合法字符与已有 {@code %XX} 转义原样保留，仅对非法字符
     * （如裸中文）按 UTF-8 percent-encode——{@code URI.create} 遇裸非 ASCII 直接抛异常，
     * {@code UriComponentsBuilder#encode} 又会把已有转义二次编码，故手工按需编码。
     */
    private URI toUri(String target) {
        StringBuilder sb = new StringBuilder(target.length() + 16);
        for (int i = 0; i < target.length();) {
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

    /** RFC 3986 unreserved + reserved + '%'（已有转义保留）. */
    private static final String URI_LEGAL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%";
}

package com.frame.me.sso.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link SsoCallbackController} 单元测试：内置落地页可加载且包含回调转跳关键逻辑；
 * 服务端回调端点 code 换会话编排 + state 站内校验（防 open redirect）.
 *
 * @author frame-me
 */
class SsoCallbackControllerTest {

    private final SsoAuthService ssoAuthService = mock(SsoAuthService.class);
    private final SsoCallbackController controller = new SsoCallbackController(ssoAuthService);

    /**
     * 页面资源存在且包含回调处理的关键标记：
     * 读取 code/state、state 站内校验、防 open redirect 回落、hash 携带 code 转跳.
     */
    @Test
    void index_loadsBundledHtmlWithCallbackLogic() throws Exception {
        var resource = controller.index();
        assertThat(resource.exists()).isTrue();

        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(html).contains("URLSearchParams");
        assertThat(html).contains("params.get('code')");
        assertThat(html).contains("params.get('state')");
        // state 校验正则：仅站内相对路径，拒绝 // 协议相对与反斜杠（防 open redirect）
        assertThat(html).contains("^\\/(?!\\/)");
        assertThat(html).contains("window.location.replace");
        // code 经 hash 携带（不进服务端日志/Referer）
        assertThat(html).contains("#code=");
    }

    /**
     * 服务端回调：合法 state 时换会话后 302 到 state 地址.
     */
    @Test
    void callback_exchangesCodeAndRedirectsToState() {
        ResponseEntity<Void> res = controller.callback("auth-code-1", "/api/log/page");

        verify(ssoAuthService).ssoLogin("auth-code-1");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(res.getHeaders().getLocation()).hasToString("/api/log/page");
    }

    /**
     * 非法 state（协议相对/绝对地址/反斜杠/空白/null）一律回落 {@code /}，防 open redirect.
     */
    @Test
    void callback_invalidStateFallsBackToRoot() {
        assertThat(controller.callback("c", "//evil.com").getHeaders().getLocation()).hasToString("/");
        assertThat(controller.callback("c", "https://evil.com").getHeaders().getLocation()).hasToString("/");
        assertThat(controller.callback("c", "/\\evil").getHeaders().getLocation()).hasToString("/");
        assertThat(controller.callback("c", "/a b").getHeaders().getLocation()).hasToString("/");
        assertThat(controller.callback("c", null).getHeaders().getLocation()).hasToString("/");
    }

    /**
     * state 携带 query 参数（分享链接场景）：原样回跳，参数不丢失.
     */
    @Test
    void callback_preservesQueryParamsInState() {
        ResponseEntity<Void> res = controller.callback("c", "/api/log/page?type=error&page=2");
        assertThat(res.getHeaders().getLocation()).hasToString("/api/log/page?type=error&page=2");
    }

    /**
     * state 携带 hash 片段（hash 路由 SPA 场景）：方式 B 的 code 不经浏览器，
     * 片段无冲突，原样保留供前端路由定位.
     */
    @Test
    void callback_preservesFragmentInState() {
        ResponseEntity<Void> res = controller.callback("c", "/#/log/page?x=1");
        assertThat(res.getHeaders().getLocation()).hasToString("/#/log/page?x=1");
    }

    /**
     * 非法字符按需编码：裸中文按 UTF-8 percent-encode（不抛异常），
     * 已有 {@code %XX} 转义原样保留（不二次编码）.
     */
    @Test
    void callback_encodesIllegalCharsOnly() {
        assertThat(controller.callback("c", "/search?q=你好").getHeaders().getLocation())
                .hasToString("/search?q=%E4%BD%A0%E5%A5%BD");
        assertThat(controller.callback("c", "/search?q=%E4%BD%A0").getHeaders().getLocation())
                .hasToString("/search?q=%E4%BD%A0");
    }
}

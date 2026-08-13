package com.frame.me.sso.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SsoIndexController} 单元测试：内置落地页可加载且包含回调转跳关键逻辑.
 *
 * @author frame-me
 */
class SsoIndexControllerTest {

    private final SsoIndexController controller = new SsoIndexController();

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
}

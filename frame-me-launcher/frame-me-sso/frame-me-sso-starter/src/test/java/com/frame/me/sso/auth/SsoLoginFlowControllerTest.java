package com.frame.me.sso.auth;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sso.config.SsoClientProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SsoLoginFlowController} 单元测试：state 签发（Redis + Cookie nonce 绑定）、
 * 一次性消费、登录 CSRF 拦截，以及 SPA/Header 与服务端 Cookie 两种回调模式.
 *
 * @author frame-me
 */
class SsoLoginFlowControllerTest {

    private final SsoAuthService ssoAuthService = mock(SsoAuthService.class);
    private final SsoClientProperties properties = properties();
    /** 用内存 Map 模拟 Redis 的 SET/GETDEL 语义，验证一次性消费行为. */
    private final Map<String, String> redis = new HashMap<>();
    private final SsoStateStore stateStore = new SsoStateStore(properties, redisTemplate(redis));
    private final SsoLoginFlowController controller = new SsoLoginFlowController(ssoAuthService, properties, stateStore);

    @Test
    void index_withoutCodeLoadsBundledLandingPage() {
        var resource = controller.index();
        assertThat(resource.exists()).isTrue();
    }

    /**
     * 服务端回调：合法 state + 匹配 nonce Cookie 时换会话后 302 到绑定地址.
     */
    @Test
    void callback_exchangesCodeWhenStateAndNonceCookieMatch() {
        Flow flow = startFlow("/api/log/page");

        ResponseEntity<Void> res = controller.callback("auth-code-1", flow.state(),
                requestWith(flow.cookie()), new MockHttpServletResponse());

        verify(ssoAuthService).ssoLogin("auth-code-1");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(res.getHeaders().getLocation()).hasToString("/api/log/page");
    }

    /**
     * 登录 CSRF：攻击者自己走一遍发起流程拿到合法 state，但受害者浏览器没有
     * 对应 nonce Cookie，必须在换 token 前拒绝.
     */
    @Test
    void callback_rejectsStateWithoutMatchingNonceCookie() {
        Flow attackerFlow = startFlow("/");

        assertThatThrownBy(() -> controller.callback("attacker-code", attackerFlow.state(),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_CREDENTIAL.getCode()));

        verify(ssoAuthService, never()).ssoLogin("attacker-code");
    }

    /**
     * state 必须一次性消费（GETDEL），已经成功使用的回调不能重放.
     */
    @Test
    void callback_rejectsReplayedState() {
        Flow flow = startFlow("/");
        controller.callback("first-code", flow.state(), requestWith(flow.cookie()), new MockHttpServletResponse());

        assertThatThrownBy(() -> controller.callback("replayed-code", flow.state(),
                requestWith(flow.cookie()), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class);
        verify(ssoAuthService, times(1)).ssoLogin("first-code");
        verify(ssoAuthService, never()).ssoLogin("replayed-code");
    }

    /**
     * 绑定的目标地址携带 query 参数时原样回跳，参数不丢失.
     */
    @Test
    void callback_preservesQueryParamsInStoredTarget() {
        Flow flow = startFlow("/api/log/page?type=error&page=2");
        ResponseEntity<Void> res = controller.callback("c", flow.state(),
                requestWith(flow.cookie()), new MockHttpServletResponse());
        assertThat(res.getHeaders().getLocation()).hasToString("/api/log/page?type=error&page=2");
    }

    /**
     * 绑定的目标地址携带 hash 片段时（hash 路由 SPA 场景），方式 B 的 code 不经浏览器，
     * 片段无冲突，原样保留供前端路由定位.
     */
    @Test
    void callback_preservesFragmentInStoredTarget() {
        Flow flow = startFlow("/#/log/page?x=1");
        ResponseEntity<Void> res = controller.callback("c", flow.state(),
                requestWith(flow.cookie()), new MockHttpServletResponse());
        assertThat(res.getHeaders().getLocation()).hasToString("/#/log/page?x=1");
    }

    /**
     * 非法字符按需编码：裸中文按 UTF-8 percent-encode（不抛异常），
     * 已有 {@code %XX} 转义原样保留（不二次编码）.
     */
    @Test
    void callback_encodesIllegalCharsOnly() {
        Flow first = startFlow("/search?q=你好");
        assertThat(controller.callback("c", first.state(), requestWith(first.cookie()), new MockHttpServletResponse())
                .getHeaders().getLocation()).hasToString("/search?q=%E4%BD%A0%E5%A5%BD");
        Flow second = startFlow("/search?q=%E4%BD%A0");
        assertThat(controller.callback("c", second.state(), requestWith(second.cookie()), new MockHttpServletResponse())
                .getHeaders().getLocation()).hasToString("/search?q=%E4%BD%A0");
    }

    /**
     * 同一浏览器并发两个登录流程：Cookie 按 state 前缀命名，互不覆盖，各自可消费.
     */
    @Test
    void callback_concurrentFlowsDoNotClobberEachOther() {
        Flow first = startFlow("/first");
        Flow second = startFlow("/second");

        ResponseEntity<Void> res = controller.callback("c", first.state(),
                requestWith(first.cookie(), second.cookie()), new MockHttpServletResponse());
        assertThat(res.getHeaders().getLocation()).hasToString("/first");
        res = controller.callback("c", second.state(), requestWith(second.cookie()), new MockHttpServletResponse());
        assertThat(res.getHeaders().getLocation()).hasToString("/second");
    }

    @Test
    void indexCallback_rejectsUnknownState() {
        assertThatThrownBy(() -> controller.indexCallback("attacker-code", "attacker-state",
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void indexCallback_movesCodeToFragmentAfterConsumingState() {
        Flow flow = startFlow("/dashboard?tab=security");

        ResponseEntity<Void> response = controller.indexCallback("auth-code-1", flow.state(),
                requestWith(flow.cookie()), new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .hasToString("/dashboard?tab=security#code=auth-code-1");
        verify(ssoAuthService, never()).ssoLogin("auth-code-1");
    }

    @Test
    void httpFlow_issuesStateWithNonceCookieAndConsumesBoth() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("me.sso.client.authorize-path", "/sso-authorize")
                .addPlaceholderValue("me.sso.client.index-path", "/index")
                .addPlaceholderValue("me.sso.client.callback-path", "/callback")
                .build();

        MvcResult started = mockMvc.perform(get("/sso-authorize").param("target", "/dashboard"))
                .andExpect(status().isFound())
                .andReturn();
        String state = UriComponentsBuilder.fromUriString(started.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("state");
        String setCookie = started.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Secure");
        Cookie nonceCookie = parseSetCookie(setCookie);
        assertThat(nonceCookie.getName()).startsWith("sso_sn_" + state.substring(0, 8));

        mockMvc.perform(get("/callback")
                        .param("code", "http-code")
                        .param("state", state)
                        .cookie(nonceCookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/dashboard"));

        verify(ssoAuthService).ssoLogin("http-code");
    }

    /** 纯 HTTP 对内部署可显式关闭 Secure；默认开启由 {@link #httpFlow_issuesStateWithNonceCookieAndConsumesBoth} 锁定. */
    @Test
    void authorize_omitsSecureAttributeWhenCookieSecureDisabled() {
        properties.setCookieSecure(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.authorize("/dashboard", "openid", response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly").contains("SameSite=Lax")
                .doesNotContain("Secure");
    }

    /** 走一遍发起流程，返回签发的 state 与 nonce Cookie. */
    private Flow startFlow(String target) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<Void> res = controller.authorize(target, "openid", response);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String state = UriComponentsBuilder.fromUri(res.getHeaders().getLocation())
                .build().getQueryParams().getFirst("state");
        return new Flow(state, parseSetCookie(response.getHeader(HttpHeaders.SET_COOKIE)));
    }

    private MockHttpServletRequest requestWith(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        return request;
    }

    private Cookie parseSetCookie(String setCookie) {
        assertThat(setCookie).isNotBlank();
        String pair = setCookie.split(";", 2)[0];
        return new Cookie(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisTemplate(Map<String, String> store) {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.getAndDelete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)));
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(ops);
        return template;
    }

    private SsoClientProperties properties() {
        SsoClientProperties value = new SsoClientProperties();
        value.setBaseUrl("http://sso.example");
        value.setAppId("app-1");
        value.setRedirectUri("http://rp.example/callback");
        return value;
    }

    private record Flow(String state, Cookie cookie) {
    }
}

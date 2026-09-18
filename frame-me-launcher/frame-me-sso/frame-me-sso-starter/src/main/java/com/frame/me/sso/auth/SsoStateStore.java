package com.frame.me.sso.auth;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sso.config.SsoClientProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 一次性 OAuth state 存储：Redis 存 state，Cookie nonce 绑定浏览器.
 *
 * <p>签发时写 Redis（key = {@value #KEY_PREFIX} + state，value = nonce + 换行 + target，
 * TTL = {@code me.sso.client.state-ttl}），并种一个按 state 前缀命名的 HttpOnly Cookie
 * 持有 nonce。回调时 GETDEL 原子消费（并发/重放只有一个请求能拿到值），再比对 Cookie
 * 中的 nonce——state 值本身不绑定会话也能防伪造（高熵随机 + 一次性），Cookie nonce
 * 额外挡住登录 CSRF（攻击者自己走一遍发起流程拿到合法 state 骗受害者回调，受害者
 * 浏览器没有对应 Cookie）。不依赖 HttpSession，集群多节点任意节点均可消费。</p>
 */
@RequiredArgsConstructor
public class SsoStateStore {

    private static final String KEY_PREFIX = "sso:login:state:";
    private static final String COOKIE_PREFIX = "sso_sn_";
    /** Cookie 名取 state 前几位，支持同一浏览器并发多个登录流程互不覆盖. */
    private static final int COOKIE_NAME_STATE_CHARS = 8;
    private static final int STATE_BYTES = 32;
    private static final int NONCE_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SsoClientProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 签发 state：写 Redis 并种 nonce Cookie，返回拼进 SSO authorize URL 的 state.
     */
    public String issue(HttpServletResponse response, String target) {
        String state = randomToken(STATE_BYTES);
        String nonce = randomToken(NONCE_BYTES);
        // target 已 normalize 不含空白，换行分隔安全
        redisTemplate.opsForValue().set(KEY_PREFIX + state,
                nonce + "\n" + normalizeTarget(target), properties.getStateTtl());
        writeCookie(response, cookieName(state), nonce, (int) properties.getStateTtl().toSeconds());
        return state;
    }

    /**
     * 消费 state：GETDEL 原子取出，校验 Cookie nonce 后清除 Cookie，返回绑定的 target.
     * 伪造/过期/已重放/nonce 不匹配一律抛 4001.
     */
    public String consume(HttpServletRequest request, HttpServletResponse response, String state) {
        if (state == null || state.length() < COOKIE_NAME_STATE_CHARS) {
            throw invalidState();
        }
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
        if (value == null) {
            throw invalidState();
        }
        // 无论 nonce 校验成败都清掉对应 Cookie（值已 GETDEL，cookie 留着也是死的）
        writeCookie(response, cookieName(state), "", 0);
        int sep = value.indexOf('\n');
        String nonce = value.substring(0, sep);
        String target = value.substring(sep + 1);
        String cookieNonce = readCookie(request, cookieName(state));
        if (cookieNonce == null
                || !MessageDigest.isEqual(nonce.getBytes(), cookieNonce.getBytes())) {
            throw invalidState();
        }
        return target;
    }

    private String cookieName(String state) {
        return COOKIE_PREFIX + state.substring(0, COOKIE_NAME_STATE_CHARS);
    }

    /** jakarta Cookie 不支持 SameSite，手写 Set-Cookie 头；Secure 交由 HTTPS 部署自行加网关层保证. */
    private void writeCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String normalizeTarget(String target) {
        if (target == null || !target.matches("^/(?!/)[^\\\\\\s]*$")) {
            return "/";
        }
        return target;
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private BusinessException invalidState() {
        return new BusinessException(ResultCode.BAD_CREDENTIAL, "SSO state 无效、已过期或已使用");
    }
}

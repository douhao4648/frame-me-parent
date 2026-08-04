package com.frame.me.base.config;

import com.frame.me.base.result.ResultCode;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * CSRF 跨站请求伪造防护自动配置.
 *
 * <p>基于 Origin/Referer 头校验：对状态变更方法（POST/PUT/PATCH/DELETE）验证请求来源，
 * 不匹配则返回 403。纯服务间调用（无浏览器 Origin/Referer 头）自动放行。</p>
 *
 * <p>Filter 优先级在安全头过滤器（{@code HIGHEST_PRECEDENCE + 1}）之后、
 * 认证过滤器（{@code HIGHEST_PRECEDENCE + 100}）之前，确保未授权来源在认证前被拒绝。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "me.csrf", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CsrfProperties.class)
@AutoConfigureAfter(BaseAutoConfiguration.class)
@RequiredArgsConstructor
public class CsrfAutoConfiguration {

    /** 需要 CSRF 校验的 HTTP 方法. */
    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CsrfProperties properties;
    private final IFilterErrorResponseWriter errorResponseWriter;

    @Bean
    public FilterRegistrationBean<Filter> csrfFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CsrfValidationFilter(properties, errorResponseWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        registration.addUrlPatterns("/*");
        // 仅拦截 REQUEST 分发：FORWARD/INCLUDE 为同一请求的服务端内部转发，
        // 原请求已通过 CSRF 校验（同源），重复校验无额外安全收益；
        // ERROR dispatch 放行避免 CSRF 校验失败掩盖真实错误状态码
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        log.info("CsrfFilter registered (Origin/Referer validation): excludePaths={}, extraAllowedOrigins={}",
                properties.getExcludePaths(), properties.getAllowedOrigins());
        return registration;
    }

    /**
     * CSRF 校验过滤器：验证 Origin/Referer 头。
     *
     * <p>安全策略（fail-closed）：校验失败时返回 403，异常时也返回 403（防御性拒绝）.</p>
     */
    @RequiredArgsConstructor
    private static class CsrfValidationFilter implements Filter {

        private final CsrfProperties properties;
        private final IFilterErrorResponseWriter errorResponseWriter;
        private final PathMatcher pathMatcher = new AntPathMatcher();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // 不校验 GET/HEAD/OPTIONS/TRACE（getMethod() 规范保证返回大写）
            if (!PROTECTED_METHODS.contains(httpRequest.getMethod())) {
                chain.doFilter(request, response);
                return;
            }

            // 排除路径不校验
            if (isExcluded(httpRequest)) {
                chain.doFilter(request, response);
                return;
            }

            // 提取请求来源 Origin
            String origin = extractOrigin(httpRequest);
            if (origin == null || origin.isBlank()) {
                // 无 Origin/Referer 头：默认 fail-open 放行（兼容服务间调用与老旧客户端）；
                // 严格模式（me.csrf.strict-mode=true）下 fail-closed，按 CSRF 攻击拒绝，
                // 适用于 Cookie 传 token 场景与 SameSite 形成纵深防御
                if (properties.isStrictMode()) {
                    log.warn("CSRF 校验失败（无来源头，严格模式）: method={}, uri={}",
                            httpRequest.getMethod(), httpRequest.getRequestURI());
                    errorResponseWriter.write(httpResponse, ResultCode.FORBIDDEN, "CSRF 校验失败");
                    return;
                }
                chain.doFilter(request, response);
                return;
            }

            // 校验来源
            if (isOriginAllowed(origin, httpRequest)) {
                chain.doFilter(request, response);
                return;
            }

            log.warn("CSRF 校验失败: method={}, uri={}, origin={}, host={}",
                    httpRequest.getMethod(), httpRequest.getRequestURI(),
                    origin, httpRequest.getHeader("Host"));
            errorResponseWriter.write(httpResponse, ResultCode.FORBIDDEN, "CSRF 校验失败");
        }

        // ponytail: excludePaths 默认空，配置项应控制在个位数（webhook 回调等少数端点），
        // stream anyMatch 足够；若扩展到数十条路径，替换为预编译 AntPathMatcher 集合
        private boolean isExcluded(HttpServletRequest request) {
            if (properties.getExcludePaths().isEmpty()) {
                return false;
            }
            String uri = org.springframework.web.util.UrlPathHelper.defaultInstance
                    .getPathWithinApplication(request);
            return properties.getExcludePaths().stream()
                    .anyMatch(pattern -> pathMatcher.match(pattern, uri));
        }

        /**
         * 从请求中提取来源 Origin：优先 {@code Origin}，其次 {@code Referer} 中的源.
         */
        private String extractOrigin(HttpServletRequest request) {
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank()) {
                return origin.strip();
            }
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    URI uri = new URI(referer);
                    String scheme = uri.getScheme();
                    String host = uri.getHost();
                    int port = uri.getPort();
                    if (scheme != null && host != null) {
                        if (port > 0 && port != defaultPort(scheme)) {
                            return scheme + "://" + host + ":" + port;
                        }
                        return scheme + "://" + host;
                    }
                } catch (URISyntaxException ignored) {
                    // Referer 格式异常，按无来源处理
                }
            }
            return null;
        }

        /**
         * 判断来源是否合法：同源（请求 Host）或命中配置的额外允许来源.
         */
        private boolean isOriginAllowed(String origin, HttpServletRequest request) {
            // 同源：比对请求的 Host
            if (isSameOrigin(origin, request)) {
                return true;
            }
            // 额外配置的来源
            return properties.getAllowedOrigins().stream()
                    .anyMatch(allowed -> originMatches(origin, allowed));
        }

        /**
         * 判断 origin 是否与请求 Host 同源.
         *
         * <p>比对 scheme://host:port 三段：host 大小写不敏感，默认端口与省略端口等价.</p>
         */
        private boolean isSameOrigin(String origin, HttpServletRequest request) {
            try {
                URI originUri = new URI(origin);
                String requestScheme = request.getScheme();
                String requestHost = request.getServerName();
                int requestPort = request.getServerPort();

                if (!requestScheme.equalsIgnoreCase(originUri.getScheme())) {
                    return false;
                }
                if (!requestHost.equalsIgnoreCase(originUri.getHost())) {
                    return false;
                }
                int originPort = originUri.getPort();
                if (originPort < 0) {
                    originPort = defaultPort(originUri.getScheme());
                }
                return originPort == requestPort;
            } catch (URISyntaxException e) {
                return false;
            }
        }

        /**
         * 判断 origin 是否匹配某允许来源表达式.
         *
         * <p>支持完整源（{@code https://example.com:8080}）和端口通配（{@code https://example.com:*}），
         * host 大小写不敏感.</p>
         */
        private boolean originMatches(String origin, String allowed) {
            // 端口通配：先做字符串级 scheme+host 匹配（URI 解析不认 :*）
            if (allowed.endsWith(":*")) {
                String allowedNoPort = allowed.substring(0, allowed.length() - 2);
                try {
                    URI allowedUri = new URI(allowedNoPort);
                    URI originUri = new URI(origin);
                    return originUri.getScheme().equalsIgnoreCase(allowedUri.getScheme())
                            && originUri.getHost().equalsIgnoreCase(allowedUri.getHost());
                } catch (URISyntaxException e) {
                    return false;
                }
            }
            // 完整匹配（scheme + host + port）
            try {
                URI originUri = new URI(origin);
                URI allowedUri = new URI(allowed);
                if (!originUri.getScheme().equalsIgnoreCase(allowedUri.getScheme())) {
                    return false;
                }
                if (!originUri.getHost().equalsIgnoreCase(allowedUri.getHost())) {
                    return false;
                }
                int originPort = originUri.getPort();
                if (originPort < 0) {
                    originPort = defaultPort(originUri.getScheme());
                }
                int allowedPort = allowedUri.getPort();
                if (allowedPort < 0) {
                    allowedPort = defaultPort(allowedUri.getScheme());
                }
                return originPort == allowedPort;
            } catch (URISyntaxException e) {
                return false;
            }
        }

        private static int defaultPort(String scheme) {
            return "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
    }
}

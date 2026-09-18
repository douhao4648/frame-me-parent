package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import com.frame.me.gateway.config.GatewayConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置版应用认证器：对齐 APISIX hmac-auth（draft-cavage HTTP Signatures）的 HMAC-SHA256 签名.
 *
 * <p>请求头契约（详见 docs/guides/gateway.md）：</p>
 * <ul>
 *   <li>{@code Authorization: Signature keyId="<appKey>",algorithm="hmac-sha256",headers="date @request-target",signature="<base64>"}</li>
 *   <li>{@code Date}：HTTP GMT 日期（RFC 1123），与网关时钟偏差超过 300 秒拒绝（对齐 APISIX clock_skew 默认值，防重放）</li>
 *   <li>{@code X-Nonce}（可选）：客户端在 {@code headers} 列表中声明 {@code x-nonce} 并提供该头时，
 *   网关对 {@code keyId+nonce} 做 Redis {@code SET NX}（TTL=时钟窗）一次性消费——重放请求第二次
 *   到达即拒。不声明则维持 APISIX 基线（仅时钟窗）。nonce 为显式 opt-in，Redis 故障时 fail-closed</li>
 * </ul>
 *
 * <p>注意 body 不在默认签名范围内：需要防篡改的客户端可把 {@code digest} 等头加进
 * {@code headers} 签名列表（机制已支持签名任意头），网关按声明原样校验。</p>
 *
 * <p>待签串（{@code \n} 拼接，尾随 {@code \n}）：{@code keyId} 为首行，随后按客户端声明的
 * {@code headers} 顺序逐行 {@code 头名: 值}（头名保留声明的原样大小写），其中 {@code @request-target}
 * 展开为 {@code METHOD request-uri}（大写方法，raw path + query string，对齐 APISIX {@code request_uri}）。
 * 签名 = base64(HmacSHA256(secret, 待签串))，与 APISIX 一致比较原始 HMAC 字节。</p>
 *
 * <p>与 APISIX 的三处有意识收窄/增强：仅支持 hmac-sha256（APISIX 默认还允许 sha1/sha512）；
 * 强制 {@code headers} 覆盖 {@code date} + {@code @request-target}（防降级——缺 date 即无防重放）；
 * 签名比较用常量时间 {@link MessageDigest#isEqual} 防时序侧信道（APISIX 为直接相等）。</p>
 *
 * <p>凭证来自 {@code me.gateway.auth.apps[]}（配置中心下发，secret 支持加密）。
 * 客户端可用 {@link #httpDate()} / {@link #sign} / {@link #authorizationHeader} 构造请求；
 * 未来让渡到 APISIX/MSE 时客户端契约不变。</p>
 *
 * @author frame-me
 */
@Slf4j
public class ConfigAppAuthenticator implements IAppAuthenticator {

    /**
     * Authorization 头 scheme 前缀（与过滤器凭证识别分发共用同一常量）.
     */
    private static final String AUTH_SCHEME = GatewayConstant.APP_AUTH_SCHEME;
    /**
     * 唯一支持的签名算法（APISIX allowed_algorithms 收窄到 sha256）.
     */
    private static final String ALGORITHM = "hmac-sha256";
    /**
     * 时钟偏差容差（防重放），对齐 APISIX clock_skew 默认 300 秒.
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(300);
    /**
     * 客户端必须覆盖的签名头（防降级：缺 date 无防重放，缺 @request-target 无方法/路径绑定）.
     */
    private static final List<String> REQUIRED_SIGNED_HEADERS = List.of("date", "@request-target");
    /**
     * {@link #sign} 辅助方法使用的签名头顺序（与校验侧按声明顺序构建一致）.
     */
    private static final String DEFAULT_HEADERS_PARAM = "date @request-target";
    /**
     * Authorization 参数解析：逗号分隔的 {@code key="value"}（对齐 APISIX 的 \w+ 键约束）.
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\s*(\\w+)=\"(.*?)\"");
    /**
     * 可选防重放 nonce 头名：客户端须在 {@code headers} 签名列表中声明才生效（防降级）.
     */
    private static final String NONCE_HEADER = "x-nonce";
    /**
     * nonce 一次性消费的 Redis key 前缀（keyId 隔离，防跨应用串用）.
     */
    private static final String NONCE_KEY_PREFIX = "gateway:hmac:nonce:";
    /**
     * nonce 长度上限（防异常长值刷 Redis）.
     */
    private static final int MAX_NONCE_LENGTH = 128;
    private final Map<String, String> secretByAppKey;
    private final ReactiveStringRedisTemplate redisTemplate;

    public ConfigAppAuthenticator(List<GatewayAuthProperties.AppCredential> apps) {
        this(apps, null);
    }

    /**
     * @param redisTemplate nonce 去重存储（reactive，不阻塞事件循环）；为 {@code null} 时声明了 nonce 的请求 fail-closed 拒绝
     */
    public ConfigAppAuthenticator(List<GatewayAuthProperties.AppCredential> apps,
                                  ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.secretByAppKey = apps.stream()
                .filter(app -> app.getAppKey() != null && !app.getAppKey().isBlank())
                .collect(Collectors.toMap(GatewayAuthProperties.AppCredential::getAppKey,
                        GatewayAuthProperties.AppCredential::getSecret, (a, b) -> b));
    }

    /**
     * 当前时间的 HTTP Date（GMT，RFC 1123），供客户端/测试构造 {@code Date} 头.
     */
    public static String httpDate() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 计算签名（base64），与网关校验同一约定；固定签名头顺序 {@code date @request-target}.
     *
     * @param path request-uri（raw path + query string，如 {@code /api/data?x=1}），与待签串展开一致
     */
    public static String sign(String secret, String keyId, String method, String path, String gmtDate) {
        String signingString = keyId + "\n" + "date: " + gmtDate + "\n" + method + " " + path + "\n";
        return Base64.getEncoder().encodeToString(hmacSha256(secret, signingString));
    }

    /**
     * 拼 {@code Authorization} 头值，供客户端/测试构造请求.
     */
    public static String authorizationHeader(String keyId, String signature) {
        return AUTH_SCHEME + "keyId=\"" + keyId + "\",algorithm=\"" + ALGORITHM
                + "\",headers=\"" + DEFAULT_HEADERS_PARAM + "\",signature=\"" + signature + "\"";
    }

    /**
     * HmacSHA256 原始字节（JDK 必备算法，异常不可达）.
     */
    private static byte[] hmacSha256(String secret, String signingString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    /**
     * 解析 Authorization 头参数为 key→value（APISIX 同款逐字段匹配）.
     */
    private static Map<String, String> parseAuthParams(String authString) {
        Map<String, String> params = new LinkedHashMap<>();
        var matcher = PARAM_PATTERN.matcher(authString);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    /**
     * 构建待签串：keyId 首行 + 按声明顺序的签名头逐行 {@code 头名: 值}（保留声明的原样大小写），尾随换行.
     */
    private static String buildSigningString(String keyId, List<String> signedHeaders, ServerHttpRequest request) {
        StringBuilder sb = new StringBuilder(keyId).append('\n');
        for (String header : signedHeaders) {
            if ("@request-target".equals(header)) {
                // 对齐 APISIX request_uri：大写方法 + raw path + query string（均未解码）
                sb.append(request.getMethod().name()).append(' ').append(rawRequestUri(request)).append('\n');
                continue;
            }
            String value = request.getHeaders().getFirst(header);
            if (value == null) {
                return null;
            }
            sb.append(header).append(": ").append(value).append('\n');
        }
        return sb.toString();
    }

    /**
     * raw request-uri（raw path + query string），对齐 APISIX {@code ctx.var.request_uri}.
     */
    private static String rawRequestUri(ServerHttpRequest request) {
        String rawPath = request.getURI().getRawPath();
        String rawQuery = request.getURI().getRawQuery();
        return rawQuery == null ? rawPath : rawPath + '?' + rawQuery;
    }

    /**
     * Date 头新鲜度校验：缺失/非 RFC 1123/超时钟偏差容差均拒绝.
     */
    private static boolean isDateFresh(String date) {
        if (date == null) {
            return false;
        }
        final Instant instant;
        try {
            instant = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return false;
        }
        return Math.abs(Instant.now().toEpochMilli() - instant.toEpochMilli()) <= CLOCK_SKEW.toMillis();
    }

    /**
     * base64 解码，非法输入返回 {@code null}（视为验签失败而非异常）.
     */
    private static byte[] decodeBase64(String signature) {
        try {
            return Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Mono<String> authenticate(ServerHttpRequest request) {
        String authz = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authz == null || !authz.startsWith(AUTH_SCHEME)) {
            return Mono.empty();
        }
        Map<String, String> params = parseAuthParams(authz.substring(AUTH_SCHEME.length()));
        String keyId = params.get("keyId");
        String signature = params.get("signature");
        if (keyId == null || signature == null) {
            return Mono.empty();
        }
        if (!ALGORITHM.equals(params.get("algorithm"))) {
            log.warn("应用鉴权失败：keyId={} 不支持的 algorithm={}", keyId, params.get("algorithm"));
            return Mono.empty();
        }
        String secret = secretByAppKey.get(keyId);
        if (secret == null) {
            log.warn("应用鉴权失败：未知 keyId={}", keyId);
            return Mono.empty();
        }
        List<String> signedHeaders = params.containsKey("headers")
                ? Arrays.asList(params.get("headers").split(" "))
                : List.of("date");
        if (!signedHeaders.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toSet())
                .containsAll(REQUIRED_SIGNED_HEADERS)) {
            log.warn("应用鉴权失败：keyId={} 签名头列表未覆盖 {}", keyId, REQUIRED_SIGNED_HEADERS);
            return Mono.empty();
        }
        if (!isDateFresh(request.getHeaders().getFirst(HttpHeaders.DATE))) {
            log.warn("应用鉴权失败：keyId={} Date 缺失/格式非法/超时钟偏差", keyId);
            return Mono.empty();
        }
        String signingString = buildSigningString(keyId, signedHeaders, request);
        if (signingString == null) {
            log.warn("应用鉴权失败：keyId={} 声明的签名头在请求中缺失", keyId);
            return Mono.empty();
        }
        byte[] clientSignature = decodeBase64(signature);
        // 同 APISIX 比较原始 HMAC 字节，但用常量时间比较防时序侧信道
        boolean ok = clientSignature != null
                && MessageDigest.isEqual(hmacSha256(secret, signingString), clientSignature);
        if (!ok) {
            log.warn("应用鉴权失败：keyId={} 签名校验不通过", keyId);
            return Mono.empty();
        }
        // 验签通过后才消费 nonce：未通过验签的请求不得烧掉合法客户端的 nonce
        boolean nonceDeclared = signedHeaders.stream().anyMatch(NONCE_HEADER::equalsIgnoreCase);
        if (!nonceDeclared) {
            return Mono.just(keyId);
        }
        return consumeNonce(keyId, request.getHeaders().getFirst(NONCE_HEADER))
                .flatMap(fresh -> fresh ? Mono.just(keyId) : Mono.empty());
    }

    /**
     * nonce 一次性消费：Redis {@code SET NX}（TTL=时钟偏差窗），已存在即重放.
     *
     * <p>nonce 是客户端显式 opt-in 的防重放机制，故存储不可用时 fail-closed 拒绝
     * （与默认时钟窗基线的可用性取向相反：声明了 nonce 说明客户端在意重放）。</p>
     */
    private Mono<Boolean> consumeNonce(String keyId, String nonce) {
        if (nonce == null || nonce.isBlank() || nonce.length() > MAX_NONCE_LENGTH) {
            log.warn("应用鉴权失败：keyId={} nonce 缺失/非法", keyId);
            return Mono.just(false);
        }
        if (redisTemplate == null) {
            log.warn("应用鉴权失败：keyId={} 声明了 nonce 但网关未配置 Redis", keyId);
            return Mono.just(false);
        }
        return redisTemplate.opsForValue()
                .setIfAbsent(NONCE_KEY_PREFIX + keyId + ':' + nonce, "1", CLOCK_SKEW)
                .map(Boolean.TRUE::equals)
                .doOnNext(fresh -> {
                    if (!fresh) {
                        log.warn("应用鉴权失败：keyId={} nonce 已使用（重放）", keyId);
                    }
                })
                // Redis 故障 fail-closed
                .onErrorResume(DataAccessException.class, e -> {
                    log.warn("应用鉴权失败：keyId={} nonce 存储不可用（fail-closed）", keyId, e);
                    return Mono.just(false);
                });
    }
}

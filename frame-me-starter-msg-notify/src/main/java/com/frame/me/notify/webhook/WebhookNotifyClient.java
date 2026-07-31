package com.frame.me.notify.webhook;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.config.WebhookChannelProperties;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Webhook 通知客户端实现.
 *
 * <p>通用 HTTP webhook 发送，支持可选的 HMAC-SHA256 签名。</p>
 */
@Slf4j
public class WebhookNotifyClient implements INotifyClient {

    private static final String CHANNEL_TYPE = "webhook";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final String name;
    private final WebhookChannelProperties properties;
    private final RestClient restClient;
    private final boolean includeErrorDetail;

    public WebhookNotifyClient(String name,
                               WebhookChannelProperties properties,
                               RestClient.Builder restClientBuilder) {
        this(name, properties, restClientBuilder, false);
    }

    public WebhookNotifyClient(String name,
                               WebhookChannelProperties properties,
                               RestClient.Builder restClientBuilder,
                               boolean includeErrorDetail) {
        this.name = name;
        this.properties = properties;
        this.includeErrorDetail = includeErrorDetail;
        // 复用注入的 RestClient.Builder（base 的 PoolingRestClientAutoConfiguration 提供连接池），
        // 超时由 me.restclient.pool.* 统一配置（connect 5s / response 30s），不支持 per-client 超时
        this.restClient = restClientBuilder
                .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                .build();
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        String url = properties.getUrl();
        if (url == null || url.isEmpty()) {
            return NotifyResult.fail("WEBHOOK_URL_MISSING", "Webhook url is not configured");
        }

        String body = buildBody(message);
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        if (properties.getHeaders() != null) {
                            properties.getHeaders().forEach((key, value) -> {
                                if (value != null) {
                                    headers.add(key, String.valueOf(value));
                                }
                            });
                        }
                        String secret = properties.getSecret();
                        if (secret != null && !secret.isEmpty()) {
                            headers.add(SIGNATURE_HEADER, sign(body, secret));
                        }
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Webhook sent via client '{}': url={}, status={}", name, url, response.getStatusCode().value());
            return NotifyResult.ok(String.valueOf(response.getStatusCode().value()));
        } catch (RestClientResponseException e) {
            log.warn("Webhook response error via client '{}': url={}, status={}, body={}",
                    name, url, e.getStatusCode().value(), e.getResponseBodyAsString());
            String responseMessage = includeErrorDetail
                    ? "status=" + e.getStatusCode().value() + ", body=" + e.getResponseBodyAsString()
                    : "Webhook server returned error";
            return NotifyResult.fail("WEBHOOK_RESPONSE_ERROR", responseMessage);
        } catch (Exception e) {
            log.error("Webhook send failed via client '{}': {}", name, e.getMessage(), e);
            String sendMessage = includeErrorDetail ? e.getMessage() : "Webhook send failed";
            return NotifyResult.fail("WEBHOOK_SEND_ERROR", sendMessage);
        }
    }

    private String buildBody(NotifyMessage message) {
        String content = message.getContent() == null ? "" : message.getContent();
        String title = message.getTitle() == null ? "" : message.getTitle();
        return "{\"title\":\"" + escapeJson(title) + "\",\"content\":\"" + escapeJson(content) + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign webhook request body", e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public boolean isAvailable() {
        return properties.getUrl() != null && !properties.getUrl().isEmpty();
    }
}

package com.frame.me.notify.webhook;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.config.WebhookChannelProperties;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    public WebhookNotifyClient(String name,
                               WebhookChannelProperties properties,
                               RestClient.Builder restClientBuilder) {
        this.name = name;
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        this.restClient = restClientBuilder
                .requestFactory(factory)
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
            return NotifyResult.fail("WEBHOOK_RESPONSE_ERROR",
                    "status=" + e.getStatusCode().value() + ", body=" + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Webhook send failed via client '{}': {}", name, e.getMessage(), e);
            return NotifyResult.fail("WEBHOOK_SEND_ERROR", e.getMessage());
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

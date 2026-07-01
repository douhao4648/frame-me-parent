package com.frame.me.notify.sms;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.config.SmsChannelProperties;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 短信通知客户端实现.
 *
 * <p>通用 HTTP 短信网关，发送 JSON 格式请求。各厂商可按需在 extras 中扩展字段。</p>
 */
@Slf4j
public class SmsNotifyClient implements INotifyClient {

    private static final String CHANNEL_TYPE = "sms";
    private static final String SIGNATURE_HEADER = "X-Sms-Signature";

    private final String name;
    private final SmsChannelProperties properties;
    private final RestClient restClient;

    public SmsNotifyClient(String name,
                           SmsChannelProperties properties,
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
            return NotifyResult.fail("SMS_URL_MISSING", "SMS url is not configured");
        }

        List<String> phones = message.getReceivers();
        if (phones == null || phones.isEmpty()) {
            return NotifyResult.fail("SMS_RECEIVERS_MISSING", "SMS receivers are empty");
        }

        String body = buildBody(message, phones);
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
                        String secret = properties.getAppSecret();
                        if (secret != null && !secret.isEmpty()) {
                            headers.add(SIGNATURE_HEADER, sign(body, secret));
                        }
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("SMS sent via client '{}': to={}, templateCode={}, status={}",
                    name, phones, message.getTitle(), response.getStatusCode().value());
            return NotifyResult.ok(String.valueOf(response.getStatusCode().value()));
        } catch (RestClientResponseException e) {
            log.warn("SMS response error via client '{}': status={}, body={}",
                    name, e.getStatusCode().value(), e.getResponseBodyAsString());
            return NotifyResult.fail("SMS_RESPONSE_ERROR",
                    "status=" + e.getStatusCode().value() + ", body=" + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("SMS send failed via client '{}': {}", name, e.getMessage(), e);
            return NotifyResult.fail("SMS_SEND_ERROR", e.getMessage());
        }
    }

    private String buildBody(NotifyMessage message, List<String> phones) {
        String phoneList = phones.stream()
                .map(this::escapeJson)
                .collect(Collectors.joining("\",\"", "\"", "\""));
        String templateCode = escapeJson(message.getTitle() == null ? "" : message.getTitle());
        String templateParam = escapeJson(message.getContent() == null ? "" : message.getContent());
        String signName = escapeJson(properties.getSignName() == null ? "" : properties.getSignName());
        String appKey = escapeJson(properties.getAppKey() == null ? "" : properties.getAppKey());

        return "{\"appKey\":\"" + appKey + "\","
                + "\"signName\":\"" + signName + "\","
                + "\"templateCode\":\"" + templateCode + "\","
                + "\"templateParam\":\"" + templateParam + "\","
                + "\"phones\":[" + phoneList + "]}";
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
            throw new RuntimeException("Failed to sign sms request body", e);
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

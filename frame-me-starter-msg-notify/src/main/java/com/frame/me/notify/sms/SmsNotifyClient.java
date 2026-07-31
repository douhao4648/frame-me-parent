package com.frame.me.notify.sms;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.config.SmsChannelProperties;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import cn.hutool.json.JSONUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final boolean includeErrorDetail;

    public SmsNotifyClient(String name,
                           SmsChannelProperties properties,
                           RestClient.Builder restClientBuilder) {
        this(name, properties, restClientBuilder, false);
    }

    public SmsNotifyClient(String name,
                           SmsChannelProperties properties,
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
            String responseMessage = includeErrorDetail
                    ? "status=" + e.getStatusCode().value() + ", body=" + e.getResponseBodyAsString()
                    : "SMS server returned error";
            return NotifyResult.fail("SMS_RESPONSE_ERROR", responseMessage);
        } catch (Exception e) {
            log.error("SMS send failed via client '{}': {}", name, e.getMessage(), e);
            String sendMessage = includeErrorDetail ? e.getMessage() : "SMS send failed";
            return NotifyResult.fail("SMS_SEND_ERROR", sendMessage);
        }
    }

    private String buildBody(NotifyMessage message, List<String> phones) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appKey", properties.getAppKey() == null ? "" : properties.getAppKey());
        body.put("signName", properties.getSignName() == null ? "" : properties.getSignName());
        body.put("templateCode", message.getTitle() == null ? "" : message.getTitle());
        body.put("templateParam", message.getContent() == null ? "" : message.getContent());
        body.put("phones", phones);
        return JSONUtil.toJsonStr(body);
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
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

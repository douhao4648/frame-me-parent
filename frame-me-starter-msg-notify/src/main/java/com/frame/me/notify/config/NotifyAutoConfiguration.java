package com.frame.me.notify.config;

import com.frame.me.base.notify.INotifySender;
import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.api.INotifyTemplateEngine;
import com.frame.me.notify.email.EmailNotifyClient;
import com.frame.me.notify.model.NotifyChannelType;
import com.frame.me.notify.notify.MsgNotifySender;
import com.frame.me.notify.sms.SmsNotifyClient;
import com.frame.me.notify.template.PlaceholderTemplateEngine;
import com.frame.me.notify.util.NotifyClientFactory;
import com.frame.me.notify.webhook.WebhookNotifyClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 通知模块自动配置.
 *
 * <p>根据 {@code me.notify.email} 及 {@code me.notify.webhook} 下的通道配置与
 * {@code clients} 命名配置注册各通道客户端，并初始化
 * {@link com.frame.me.notify.util.NotifyClientFactory}。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyAutoConfiguration {

    private final NotifyProperties notifyProperties;

    private final List<INotifyTemplateEngine> templateEngines;

    private final RestClient.Builder restClientBuilder;

    public NotifyAutoConfiguration(NotifyProperties notifyProperties,
                                   @Autowired(required = false) RestClient.Builder restClientBuilder) {
        this.notifyProperties = notifyProperties;
        this.templateEngines = new ArrayList<>();
        loadFreemarkerEngine();
        this.templateEngines.add(new PlaceholderTemplateEngine());
        this.restClientBuilder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
    }

    private void loadFreemarkerEngine() {
        if (!ClassUtils.isPresent("freemarker.template.Configuration", getClass().getClassLoader())) {
            log.debug("FreeMarker not found on classpath, skip FreemarkerTemplateEngine");
            return;
        }
        try {
            INotifyTemplateEngine engine = (INotifyTemplateEngine) Class.forName(
                            "com.frame.me.notify.template.FreemarkerTemplateEngine")
                    .getDeclaredConstructor()
                    .newInstance();
            this.templateEngines.add(engine);
            log.debug("FreemarkerTemplateEngine loaded");
        } catch (ReflectiveOperationException e) {
            log.warn("FreeMarker is present but failed to instantiate FreemarkerTemplateEngine", e);
        }
    }

    @PostConstruct
    public void init() {
        Map<String, INotifyClient> clients = new HashMap<>();
        Map<String, String> channelDefaults = new HashMap<>();

        // 注册默认 email 客户端
        EmailChannelProperties email = notifyProperties.getEmail();
        if (isEmailConfigured(email)) {
            clients.put("email", new EmailNotifyClient("email", email, templateEngines));
            channelDefaults.put("email", "email");
            log.info("Notify default client registered: name=email, type={}", NotifyChannelType.EMAIL.getCode());
        }

        // 注册 email 通道命名客户端
        Optional.ofNullable(email)
                .map(EmailChannelProperties::getClients)
                .orElse(Collections.emptyMap())
                .forEach((name, props) -> {
                    if (isEmailConfigured(props)) {
                        clients.put("email:" + name, new EmailNotifyClient(name, props, templateEngines));
                        log.info("Notify client registered: name=email:{}, type={}", name, NotifyChannelType.EMAIL.getCode());
                    }
                });

        // 注册默认 webhook 客户端
        WebhookChannelProperties webhook = notifyProperties.getWebhook();
        if (isWebhookConfigured(webhook)) {
            clients.put("webhook", new WebhookNotifyClient("webhook", webhook, restClientBuilder));
            channelDefaults.put("webhook", "webhook");
            log.info("Notify default client registered: name=webhook, type={}", "webhook");
        }

        // 注册 webhook 通道命名客户端
        Optional.ofNullable(webhook)
                .map(WebhookChannelProperties::getClients)
                .orElse(Collections.emptyMap())
                .forEach((name, props) -> {
                    if (isWebhookConfigured(props)) {
                        clients.put("webhook:" + name, new WebhookNotifyClient(name, props, restClientBuilder));
                        log.info("Notify client registered: name=webhook:{}, type={}", name, "webhook");
                    }
                });

        // 注册默认 sms 客户端
        SmsChannelProperties sms = notifyProperties.getSms();
        if (isSmsConfigured(sms)) {
            clients.put("sms", new SmsNotifyClient("sms", sms, restClientBuilder));
            channelDefaults.put("sms", "sms");
            log.info("Notify default client registered: name=sms, type={}", NotifyChannelType.SMS.getCode());
        }

        // 注册 sms 通道命名客户端
        Optional.ofNullable(sms)
                .map(SmsChannelProperties::getClients)
                .orElse(Collections.emptyMap())
                .forEach((name, props) -> {
                    if (isSmsConfigured(props)) {
                        clients.put("sms:" + name, new SmsNotifyClient(name, props, restClientBuilder));
                        log.info("Notify client registered: name=sms:{}, type={}", name, NotifyChannelType.SMS.getCode());
                    }
                });

        NotifyClientFactory.init(clients, channelDefaults, resolveGlobalDefault(channelDefaults));
        log.info("Notify initialize : {}", clients.keySet());
    }

    /**
     * 解析并校验全局默认客户端.
     *
     * @param channelDefaults 已注册的通道默认客户端映射
     * @return 全局默认客户端名称，无效时返回 null
     */
    private String resolveGlobalDefault(Map<String, String> channelDefaults) {
        String globalDefault = notifyProperties.getGlobalDefault();
        if (globalDefault == null || globalDefault.isBlank()) {
            return null;
        }
        Set<String> supportedChannels = Set.of("email", "webhook", "sms");
        if (!supportedChannels.contains(globalDefault)) {
            log.warn("Notify global default '{}' is not supported, supported channels are {}", globalDefault, supportedChannels);
            return null;
        }
        if (!channelDefaults.containsKey(globalDefault)) {
            log.warn("Notify global default '{}' is configured but the channel default client is not available", globalDefault);
            return null;
        }
        log.info("Notify global default client registered: name={}", globalDefault);
        return globalDefault;
    }

    /**
     * 注册通用通知发送器实现.
     *
     * @return MsgNotifySender 实例
     */
    @Bean
    @ConditionalOnClass(INotifySender.class)
    @ConditionalOnMissingBean(INotifySender.class)
    @ConditionalOnProperty(prefix = "me.notify.sender", name = "enabled", havingValue = "true", matchIfMissing = true)
    public INotifySender notifySender() {
        return new MsgNotifySender(notifyProperties);
    }

    private boolean isEmailConfigured(EmailChannelProperties email) {
        return email != null && email.getHost() != null && !email.getHost().isEmpty();
    }

    private boolean isWebhookConfigured(WebhookChannelProperties webhook) {
        return webhook != null && webhook.getUrl() != null && !webhook.getUrl().isEmpty();
    }

    private boolean isSmsConfigured(SmsChannelProperties sms) {
        return sms != null && sms.getUrl() != null && !sms.getUrl().isEmpty();
    }
}

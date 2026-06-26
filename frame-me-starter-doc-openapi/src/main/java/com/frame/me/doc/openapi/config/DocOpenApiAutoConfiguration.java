package com.frame.me.doc.openapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * OpenAPI 文档自动配置.
 *
 * <p>当类路径存在 {@link OpenAPI} 时自动启用，注册 OpenAPI 文档 bean 和 API 分组。
 * 需要通过配置 {@code frame.me.swagger.enabled=true} 开启。
 * API 分组通过 {@link GroupedOpenApiRegistrar} 根据 {@code frame.me.swagger.groups} 动态注册。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@ConditionalOnProperty(
        prefix = "frame.me.swagger",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(DocOpenApiProperties.class)
@Import(GroupedOpenApiRegistrar.class)
public class DocOpenApiAutoConfiguration {

    /**
     * 缓存 OpenAPI 单例，避免在某些场景下被重复构建.
     */
    private OpenAPI cachedOpenAPI;

    /**
     * 注册 OpenAPI 文档信息.
     *
     * @param properties OpenAPI 配置属性
     * @return OpenAPI
     */
    @Bean
    public OpenAPI openAPI(DocOpenApiProperties properties) {
        if (cachedOpenAPI != null) {
            return cachedOpenAPI;
        }
        log.info("注册 OpenAPI 文档：title={}", properties.getTitle());
        Contact contact = new Contact();
        contact.setName(properties.getContact().getName());
        contact.setEmail(properties.getContact().getEmail());
        contact.setUrl(properties.getContact().getUrl());

        Info info = new Info()
                .title(properties.getTitle())
                .description(properties.getDescription())
                .version(properties.getVersion())
                .contact(contact);

        cachedOpenAPI = new OpenAPI().info(info);
        return cachedOpenAPI;
    }
}

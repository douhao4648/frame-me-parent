package com.frame.me.doc.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI 文档配置属性.
 *
 * <p>绑定前缀 {@code me.swagger}，支持显式配置文档标题、描述、版本、联系人、API 分组等信息。
 */
@Data
@ConfigurationProperties(prefix = "me.swagger")
public class DocOpenApiProperties {

    /**
     * 是否启用 OpenAPI 文档，默认关闭.
     */
    private boolean enabled = true;

    /**
     * API 文档标题.
     */
    private String title = "Frame Me API";

    /**
     * API 文档描述.
     */
    private String description = "Frame Me 接口文档";

    /**
     * API 文档版本.
     */
    private String version = "1.0.0";

    /**
     * 联系人信息.
     */
    private final ContactProperties contact = new ContactProperties();

    /**
     * API 分组列表.
     *
     * <p>未配置时，默认注册一个名为 {@code default}、匹配所有路径的分组。
     */
    private final List<GroupProperties> groups = new ArrayList<>();

    /**
     * 联系人配置.
     */
    @Data
    public static class ContactProperties {

        /**
         * 联系人姓名.
         */
        private String name;

        /**
         * 联系人邮箱.
         */
        private String email;

        /**
         * 联系人 URL.
         */
        private String url;
    }

    /**
     * API 分组配置.
     */
    @Data
    public static class GroupProperties {

        /**
         * 分组名称.
         */
        private String name = "default";

        /**
         * 匹配路径列表.
         */
        private List<String> pathsToMatch = List.of("/**");
    }
}

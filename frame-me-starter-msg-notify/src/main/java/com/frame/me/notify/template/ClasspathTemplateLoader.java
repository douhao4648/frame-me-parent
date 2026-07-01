package com.frame.me.notify.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Classpath 模板加载器.
 */
@Slf4j
public final class ClasspathTemplateLoader {

    private static final String DEFAULT_PREFIX = "templates/notify/";
    private static final String SUFFIX = ".html";

    private ClasspathTemplateLoader() {
    }

    /**
     * 加载模板内容.
     *
     * <p>若 template 以 {@code .html} 结尾或不含 HTML 标签，则尝试从 classpath 加载；
     * 否则直接返回 template 作为内联模板。</p>
     *
     * @param template 模板名或模板内容
     * @return 模板内容
     */
    public static String load(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (template.endsWith(SUFFIX) || !containsHtmlTag(template)) {
            String path = template.endsWith(SUFFIX)
                    ? DEFAULT_PREFIX + template
                    : DEFAULT_PREFIX + template + SUFFIX;
            Resource resource = new ClassPathResource(path);
            if (resource.exists()) {
                try {
                    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.warn("Failed to load classpath template '{}': {}", path, e.getMessage());
                }
            }
        }
        return template;
    }

    private static boolean containsHtmlTag(String text) {
        return text.matches(".*</?[a-zA-Z][^>]*>.*");
    }
}

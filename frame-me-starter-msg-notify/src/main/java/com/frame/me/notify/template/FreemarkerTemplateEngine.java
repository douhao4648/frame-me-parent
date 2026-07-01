package com.frame.me.notify.template;

import com.frame.me.notify.api.INotifyTemplateEngine;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * FreeMarker 模板引擎.
 */
@Slf4j
public class FreemarkerTemplateEngine implements INotifyTemplateEngine {

    private static final String TEMPLATE_PREFIX = "templates/notify/";
    private static final String INLINE_TEMPLATE_NAME = "_inline_";
    private static final String FILE_SUFFIX = ".ftl";

    private final Configuration configuration;
    private final StringTemplateLoader stringTemplateLoader;

    public FreemarkerTemplateEngine() {
        this.configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        this.configuration.setDefaultEncoding("UTF-8");
        this.configuration.setLocalizedLookup(false);

        this.stringTemplateLoader = new StringTemplateLoader();
        this.configuration.setTemplateLoader(new freemarker.cache.MultiTemplateLoader(
                new freemarker.cache.TemplateLoader[]{
                        new ClassTemplateLoader(FreemarkerTemplateEngine.class, "/" + TEMPLATE_PREFIX),
                        stringTemplateLoader
                }));
    }

    @Override
    public String render(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        try {
            Template freeMarkerTemplate = resolveTemplate(template);
            StringWriter writer = new StringWriter();
            freeMarkerTemplate.process(params, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            log.error("FreeMarker render failed: {}", e.getMessage(), e);
            throw new RuntimeException("FreeMarker render failed: " + e.getMessage(), e);
        }
    }

    private Template resolveTemplate(String template) throws IOException {
        if (isFileTemplate(template)) {
            String name = template.endsWith(FILE_SUFFIX)
                    ? template.substring(0, template.length() - FILE_SUFFIX.length())
                    : template;
            return configuration.getTemplate(name + FILE_SUFFIX);
        }
        stringTemplateLoader.putTemplate(INLINE_TEMPLATE_NAME, template);
        return configuration.getTemplate(INLINE_TEMPLATE_NAME);
    }

    private boolean isFileTemplate(String template) {
        if (template.endsWith(FILE_SUFFIX)) {
            return true;
        }
        // 若不含 HTML 标签，视为文件名
        return !template.matches(".*</?[a-zA-Z][^>]*>.*");
    }

    @Override
    public boolean supports(String templateType) {
        return templateType == null
                || "freemarker".equalsIgnoreCase(templateType)
                || "html".equalsIgnoreCase(templateType);
    }
}

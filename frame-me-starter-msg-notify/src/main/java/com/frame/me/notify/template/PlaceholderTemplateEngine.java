package com.frame.me.notify.template;

import com.frame.me.notify.api.INotifyTemplateEngine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符模板引擎.
 *
 * <p>支持 {@code ${key}} 占位符替换，作为 FreeMarker 不可用时的 fallback，无额外依赖。</p>
 */
public class PlaceholderTemplateEngine implements INotifyTemplateEngine {

    private static final Pattern PATTERN = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*}");

    @Override
    public String render(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        String content = ClasspathTemplateLoader.load(template);
        if (params == null || params.isEmpty()) {
            return content;
        }
        Matcher matcher = PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.get(key);
            matcher.appendReplacement(result, value == null ? "" : Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Override
    public boolean supports(String templateType) {
        return "placeholder".equalsIgnoreCase(templateType);
    }
}

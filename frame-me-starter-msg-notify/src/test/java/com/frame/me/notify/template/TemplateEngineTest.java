package com.frame.me.notify.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板引擎测试.
 */
class TemplateEngineTest {

    @Test
    void freemarkerShouldRenderClasspathTemplate() {
        FreemarkerTemplateEngine engine = new FreemarkerTemplateEngine();
        String result = engine.render("order", Map.of("name", "张三", "orderNo", "ORD123", "expressNo", "SF456"));
        assertThat(result).contains("您好 张三")
                .contains("订单 ORD123")
                .contains("快递单号：SF456");
    }

    @Test
    void freemarkerShouldRenderInlineTemplate() {
        FreemarkerTemplateEngine engine = new FreemarkerTemplateEngine();
        String result = engine.render("<p>您好 ${name}</p>", Map.of("name", "李四"));
        assertThat(result).isEqualTo("<p>您好 李四</p>");
    }

    @Test
    void placeholderShouldRenderHtmlTemplate() {
        PlaceholderTemplateEngine engine = new PlaceholderTemplateEngine();
        String result = engine.render("<p>您好 ${name}，订单 ${orderNo}</p>",
                Map.of("name", "张三", "orderNo", "ORD123"));
        assertThat(result).isEqualTo("<p>您好 张三，订单 ORD123</p>");
    }

    @Test
    void placeholderShouldLoadClasspathHtmlAndReplace() {
        // classpath 不存在 order.html，会直接返回模板名，此用例验证 fallback 行为
        PlaceholderTemplateEngine engine = new PlaceholderTemplateEngine();
        String result = engine.render("order", Map.of("name", "张三"));
        assertThat(result).isEqualTo("order");
    }
}

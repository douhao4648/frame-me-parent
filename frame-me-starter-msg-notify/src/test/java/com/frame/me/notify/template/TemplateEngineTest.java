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

    /**
     * 占位符引擎认领 null templateType：FreeMarker（optional 依赖）缺席时
     * 默认路径才能真正回退到占位符替换，不会原样发出未渲染模板.
     */
    @Test
    void placeholderShouldSupportNullTemplateType() {
        PlaceholderTemplateEngine engine = new PlaceholderTemplateEngine();
        assertThat(engine.supports(null)).isTrue();
        assertThat(engine.supports("placeholder")).isTrue();
        assertThat(engine.supports("freemarker")).isFalse();
    }

    /**
     * 并发渲染不同内联模板：每个线程的结果必须匹配自己的模板，
     * 共享槽位实现下会互相覆盖导致内容串台.
     */
    @Test
    void freemarkerShouldRenderInlineTemplatesConcurrentlyWithoutCrossTalk() throws Exception {
        FreemarkerTemplateEngine engine = new FreemarkerTemplateEngine();
        int threads = 16;
        int iterations = 50;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                String template = "<p>用户" + index + "：您好 ${name}</p>";
                String expected = "<p>用户" + index + "：您好 姓名" + index + "</p>";
                start.await();
                for (int j = 0; j < iterations; j++) {
                    if (!expected.equals(engine.render(template, Map.of("name", "姓名" + index)))) {
                        return false;
                    }
                }
                return true;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }
        pool.shutdown();
    }
}

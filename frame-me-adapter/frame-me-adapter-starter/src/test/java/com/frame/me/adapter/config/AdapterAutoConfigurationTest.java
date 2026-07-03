package com.frame.me.adapter.config;

import com.frame.me.adapter.web.ResponseFilterErrorResponseWriter;
import com.frame.me.base.config.BaseAutoConfiguration;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdapterAutoConfiguration} 自动配置测试.
 *
 * @author frame-me
 */
class AdapterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BaseAutoConfiguration.class, AdapterAutoConfiguration.class));

    @Test
    void testResponseFilterErrorResponseWriterOverridesDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IFilterErrorResponseWriter.class);
            assertThat(context.getBean(IFilterErrorResponseWriter.class))
                    .isInstanceOf(ResponseFilterErrorResponseWriter.class);
        });
    }
}

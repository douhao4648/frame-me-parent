package com.frame.me.base.config;

import tools.jackson.databind.ObjectMapper;
import com.frame.me.base.advice.GlobalExceptionHandler;
import com.frame.me.base.env.EnvironmentHelper;
import com.frame.me.base.result.ResultJacksonModule;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import com.frame.me.base.web.ResultFilterErrorResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * frame-me-starter-base 自动配置.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExceptionProperties.class)
public class BaseAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler(ExceptionProperties exceptionProperties) {
        return new GlobalExceptionHandler(exceptionProperties);
    }

    @Bean
    public EnvironmentHelper environmentHelper(Environment environment) {
        return new EnvironmentHelper(environment);
    }


    @Bean
    @ConditionalOnMissingBean(ResultJacksonModule.class)
    ResultJacksonModule resultJacksonModule() {
        return new ResultJacksonModule();
    }

    /**
     * 默认 Filter 层错误响应写入器：输出 {@link com.frame.me.base.result.Result} 格式.
     */
    @Bean
    @ConditionalOnMissingBean(IFilterErrorResponseWriter.class)
    public IFilterErrorResponseWriter filterErrorResponseWriter(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new ResultFilterErrorResponseWriter(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

}

package com.frame.me.base.config;

import com.frame.me.base.advice.GlobalExceptionHandler;
import com.frame.me.base.env.EnvironmentHelper;
import com.frame.me.base.result.ResultJacksonModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * frame-me-starter-base 自动配置.
 */
@Configuration(proxyBeanMethods = false)
public class BaseAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
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

}

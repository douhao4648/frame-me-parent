package com.frame.me.base.config;

import com.frame.me.base.advice.GlobalExceptionHandler;
import com.frame.me.base.env.EnvironmentHelper;
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
}

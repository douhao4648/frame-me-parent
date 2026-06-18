package com.frame.me.base.config;

import com.frame.me.base.advice.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * frame-me-base 自动配置.
 */
@Configuration(proxyBeanMethods = false)
public class BaseAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
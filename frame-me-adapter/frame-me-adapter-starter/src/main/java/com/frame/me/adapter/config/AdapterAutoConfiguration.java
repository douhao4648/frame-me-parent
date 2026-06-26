package com.frame.me.adapter.config;

import com.frame.me.adapter.advice.Result2ResponseAdvice;
import com.frame.me.adapter.result.ResponseJacksonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置
 */
@Configuration(proxyBeanMethods = false)
public class AdapterAutoConfiguration {

    @Bean
    public Result2ResponseAdvice resultResponseBodyAdvice() {
        return new Result2ResponseAdvice();
    }

    @Bean
    ResponseJacksonModule responseJacksonModule() {
        return new ResponseJacksonModule();
    }

}

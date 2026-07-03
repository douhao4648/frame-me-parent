package com.frame.me.adapter.config;

import com.frame.me.adapter.advice.Result2ResponseAdvice;
import com.frame.me.adapter.result.ResponseJacksonModule;
import com.frame.me.adapter.web.ResponseFilterErrorResponseWriter;
import com.frame.me.base.config.BaseAutoConfiguration;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 自动配置
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(BaseAutoConfiguration.class)
public class AdapterAutoConfiguration {

    @Bean
    public Result2ResponseAdvice resultResponseBodyAdvice() {
        return new Result2ResponseAdvice();
    }

    @Bean
    ResponseJacksonModule responseJacksonModule() {
        return new ResponseJacksonModule();
    }

    /**
     * 老接口规范下覆盖默认的 Filter 层错误响应格式为 {@link com.frame.me.adapter.result.Response}.
     */
    @Bean
    @ConditionalOnMissingBean(IFilterErrorResponseWriter.class)
    public IFilterErrorResponseWriter filterErrorResponseWriter(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new ResponseFilterErrorResponseWriter(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

}

package com.frame.me.base.mybatis.util;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成工具类.
 *
 * <p>优先使用 Spring 容器中自定义的 {@link IdentifierGenerator} Bean（如项目里通过
 * {@code me.mybatis.snowflake.worker-id} 配置的生成器），若不存在则回退到
 * MyBatis-Plus 默认的 {@link DefaultIdentifierGenerator#getInstance()}。
 *
 * <p>典型用法：
 * <pre>
 * long id = SnowflakeUtil.nextId();
 * String idStr = SnowflakeUtil.nextIdStr();
 * </pre>
 */
@Slf4j
@Component
public class SnowflakeUtils implements ApplicationContextAware {

    private static IdentifierGenerator identifierGenerator;

    /**
     * 生成下一个雪花 ID.
     *
     * @return 长整型 ID
     */
    public static long nextId() {
        return identifierGenerator.nextId(null).longValue();
    }

    /**
     * 生成下一个雪花 ID 字符串.
     *
     * @return ID 字符串
     */
    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        String[] beanNames = applicationContext.getBeanNamesForType(IdentifierGenerator.class);
        if (beanNames.length > 0) {
            identifierGenerator = applicationContext.getBean(IdentifierGenerator.class);
            log.debug("SnowflakeUtil use custom IdentifierGenerator: {}", identifierGenerator.getClass().getName());
        } else {
            identifierGenerator = DefaultIdentifierGenerator.getInstance();
            log.debug("SnowflakeUtil use DefaultIdentifierGenerator");
        }
    }

}

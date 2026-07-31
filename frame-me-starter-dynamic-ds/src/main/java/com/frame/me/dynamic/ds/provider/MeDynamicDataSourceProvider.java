package com.frame.me.dynamic.ds.provider;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.creator.druid.DruidConfig;
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariCpConfig;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 根据 {@code spring.datasource.*} 自动创建 dynamic-datasource 的默认 master 数据源.
 *
 * <p>只要存在 {@code spring.datasource.url}，本 Provider 就会读取当前 Environment 中的最新
 * {@code spring.datasource.*} 属性（包括 {@code @DynamicPropertySource} 在测试阶段动态覆盖后的值），
 * 创建名为 {@code master} 的数据源。
 * 由于本 Provider 在 baomidou 的 YmlDynamicDataSourceProvider 之前加载，
 * 当 dynamic-datasource 中也显式配置了 {@code master} 时，YmlDynamicDataSourceProvider 会覆盖本 Provider 创建的 master，
 * 即显式的 dynamic master 配置优先级更高。
 */
@Slf4j
@RequiredArgsConstructor
public class MeDynamicDataSourceProvider implements DynamicDataSourceProvider {

    public static final String MASTER_NAME = "master";

    private static final String SPRING_DATASOURCE_URL = "spring.datasource.url";
    private static final String SPRING_DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String SPRING_DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final String SPRING_DATASOURCE_DRIVER = "spring.datasource.driver-class-name";
    private static final String HIKARI_SOURCE_PREFIX = "spring.datasource.hikari.";
    /** Druid 配置绑定前缀（不带末尾点），用于 Binder.bind，避免脆弱的 substring 截取. */
    private static final String DRUID_BIND_PREFIX = "spring.datasource.druid";
    private static final String DRUID_SOURCE_PREFIX = DRUID_BIND_PREFIX + ".";

    private final DefaultDataSourceCreator dataSourceCreator;
    private final ConfigurableEnvironment environment;

    @Override
    public Map<String, DataSource> loadDataSources() {
        String url = environment.getProperty(SPRING_DATASOURCE_URL);
        if (!StringUtils.hasText(url)) {
            return Collections.emptyMap();
        }

        DataSourceProperty property = new DataSourceProperty();
        property.setUrl(url);
        property.setUsername(environment.getProperty(SPRING_DATASOURCE_USERNAME));
        property.setPassword(environment.getProperty(SPRING_DATASOURCE_PASSWORD));
        property.setDriverClassName(environment.getProperty(SPRING_DATASOURCE_DRIVER));
        property.setPoolName(MASTER_NAME);
        loadHikariProperties(property);
        loadDruidProperties(property);

        DataSource dataSource = dataSourceCreator.createDataSource(property);
        return Collections.singletonMap(MASTER_NAME, dataSource);
    }

    private void loadHikariProperties(DataSourceProperty property) {
        Map<String, Object> hikariProps = new HashMap<>();
        // getPropertySources() 迭代顺序为高优先级→低优先级（命令行 > 环境变量 > 配置文件），
        // 用 putIfAbsent 让高优先级源先占位、低优先级源不覆盖，与 environment.getProperty 的解析优先级一致.
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (name.startsWith(HIKARI_SOURCE_PREFIX)) {
                    String key = name.substring(HIKARI_SOURCE_PREFIX.length());
                    hikariProps.putIfAbsent(key, source.getProperty(name));
                }
            }
        }
        if (hikariProps.isEmpty()) {
            return;
        }
        HikariCpConfig hikari = property.getHikari();
        if (hikari == null) {
            hikari = new HikariCpConfig();
            property.setHikari(hikari);
        }
        final HikariCpConfig targetHikari = hikari;
        hikariProps.forEach((key, value) -> setHikariProperty(targetHikari, key, value));
    }

    private void loadDruidProperties(DataSourceProperty property) {
        try {
            Binder binder = Binder.get(environment);
            binder.bind(DRUID_BIND_PREFIX, Bindable.of(DruidConfig.class))
                    .ifBound(property::setDruid);
        } catch (Exception e) {
            log.warn("Failed to bind Druid connection pool properties: {}", e.getMessage());
        }
    }

    private void setHikariProperty(HikariCpConfig hikari, String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            String camelKey = kebabToCamel(key);
            Method setter = findSetter(hikari.getClass(), camelKey);
            if (setter == null) {
                return;
            }
            Class<?> paramType = setter.getParameterTypes()[0];
            setter.invoke(hikari, convertValue(value, paramType));
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.warn("Failed to bind Hikari connection pool properties {}: {}", key, e.getMessage());
        }
    }

    private String kebabToCamel(String kebab) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Method findSetter(Class<?> clazz, String propertyName) {
        if (!StringUtils.hasText(propertyName)) {
            return null;
        }
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return value;
        }
        String str = value.toString();
        if (targetType == String.class) {
            return str;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(str);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(str);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(str);
        }
        // HikariCP 的 connectionTimeout / maxLifetime / idleTimeout 是 Duration 类型，
        // 用 Spring Boot 的 DurationStyle 解析（支持 30000=30000ms、30s、2m 等格式）
        if (targetType == java.time.Duration.class) {
            return org.springframework.boot.convert.DurationStyle.detect(str).parse(str);
        }
        return value;
    }
}

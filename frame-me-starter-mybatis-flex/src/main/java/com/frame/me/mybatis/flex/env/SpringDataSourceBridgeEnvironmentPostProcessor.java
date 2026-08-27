package com.frame.me.mybatis.flex.env;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code spring.datasource} → MyBatis-Flex master 数据源桥接.
 *
 * <p>当存在 {@code spring.datasource.url} 且未显式配置 {@code mybatis-flex.datasource.master.url} 时，
 * 将 {@code spring.datasource.*}（含 {@code spring.datasource.hikari.*} / {@code spring.datasource.druid.*} 连接池参数）以最低优先级
 * 属性源映射为 {@code mybatis-flex.datasource.master.*}，使 flex 官方多数据源装配把
 * {@code spring.datasource} 注册为 master 数据源，业务方无需再重复抄写一份 master 配置。
 * 显式的 {@code mybatis-flex.datasource.master.*} 逐属性覆盖本桥接；一旦显式给出
 * {@code master.url} 则视为完全自定义 master，本桥接整体退避。
 * {@code spring.datasource.type} 会翻译成 flex 的 {@code type} 别名（hikari/druid）；
 * 未配置时按池参数前缀推断（仅出现一种池参数才推断）。</p>
 *
 * <p>注册方式为 {@code META-INF/spring.factories} 的 {@code EnvironmentPostProcessor} 键——
 * 该钩子早于自动装配，无法通过 {@code AutoConfiguration.imports} 注册。
 * order 取 {@code LOWEST_PRECEDENCE - 10}：晚于 ConfigData 加载（能读到 application.yml），
 * 早于 sensi-encrypt 的解密包装（{@code LOWEST_PRECEDENCE}），
 * 桥接属性源中的 {@code ME(...)} 密文随后仍会被原位包装解密。</p>
 */
public class SpringDataSourceBridgeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 桥接属性源名称.
     */
    public static final String BRIDGE_PROPERTY_SOURCE_NAME = "flexSpringDataSourceBridge";

    private static final String MYBATIS_ENABLED = "me.mybatis.enabled";
    private static final String BRIDGE_ENABLED = "me.mybatis.datasource-bridge.enabled";
    private static final String SPRING_PREFIX = "spring.datasource.";
    private static final String SPRING_HIKARI_PREFIX = "spring.datasource.hikari.";
    private static final String SPRING_DRUID_PREFIX = "spring.datasource.druid.";
    private static final String[] POOL_PREFIXES = {SPRING_HIKARI_PREFIX, SPRING_DRUID_PREFIX};
    private static final String FLEX_MASTER_PREFIX = "mybatis-flex.datasource.master.";
    private static final String[] SCALAR_KEYS = {"url", "username", "password", "driver-class-name"};
    /** spring 数据源实现类名 → flex type 别名，未知值原样透传（flex 兼容类名）. */
    private static final Map<String, String> TYPE_ALIASES = Map.of(
            "com.zaxxer.hikari.HikariDataSource", "hikari",
            "com.alibaba.druid.pool.DruidDataSource", "druid");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if ("false".equalsIgnoreCase(environment.getProperty(MYBATIS_ENABLED))
                || "false".equalsIgnoreCase(environment.getProperty(BRIDGE_ENABLED))) {
            return;
        }
        if (!StringUtils.hasText(environment.getProperty(SPRING_PREFIX + "url"))) {
            return;
        }
        // 显式配置了 flex master 的 url，视为完全自定义，桥接退避
        if (environment.containsProperty(FLEX_MASTER_PREFIX + "url")) {
            return;
        }

        Map<String, Object> bridge = new LinkedHashMap<>();
        for (String key : SCALAR_KEYS) {
            String value = environment.getProperty(SPRING_PREFIX + key);
            if (StringUtils.hasText(value)) {
                bridge.put(FLEX_MASTER_PREFIX + key, value);
            }
        }
        // 数据源类型：显式 spring.datasource.type 优先，类名翻译成 flex 别名
        String springType = environment.getProperty(SPRING_PREFIX + "type");
        if (StringUtils.hasText(springType)) {
            bridge.put(FLEX_MASTER_PREFIX + "type", TYPE_ALIASES.getOrDefault(springType, springType));
        }

        // 连接池参数：spring.datasource.hikari.* / spring.datasource.druid.* 原样搬到 master 下
        //（flex 按 kebab→camel 反射注入 HikariConfig / DruidDataSource）
        boolean hasHikariProps = false;
        boolean hasDruidProps = false;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                for (String prefix : POOL_PREFIXES) {
                    if (name.startsWith(prefix)) {
                        hasHikariProps |= SPRING_HIKARI_PREFIX.equals(prefix);
                        hasDruidProps |= SPRING_DRUID_PREFIX.equals(prefix);
                        // 按属性源优先级先见先得
                        bridge.putIfAbsent(FLEX_MASTER_PREFIX + name.substring(prefix.length()),
                                environment.getProperty(name));
                    }
                }
            }
        }

        // 未显式配置 type 时按池参数前缀推断，避免双池依赖都在 classpath 时 flex 探测顺序选中 druid；
        // 两种池参数都出现则不猜，交回 flex 探测/显式 master.type
        if (!bridge.containsKey(FLEX_MASTER_PREFIX + "type") && hasHikariProps != hasDruidProps) {
            bridge.put(FLEX_MASTER_PREFIX + "type", hasDruidProps ? "druid" : "hikari");
        }

        // addLast：最低优先级，任何显式的 mybatis-flex.datasource.master.* 均可逐属性覆盖
        environment.getPropertySources().addLast(new MapPropertySource(BRIDGE_PROPERTY_SOURCE_NAME, bridge));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}

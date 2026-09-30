package com.frame.me.dynamic.ds.provider;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.creator.druid.DruidConfig;
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariCpConfig;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Collections;
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
 * 因此检测到显式 dynamic master（{@code spring.datasource.dynamic.datasource.master.url}）时
 * 本 Provider 直接跳过创建——否则被 putAll 覆盖的备用池已启动却无人关闭，连接与
 * housekeeping 线程会泄漏到 JVM 退出。
 */
@Slf4j
@RequiredArgsConstructor
public class MeDynamicDataSourceProvider implements DynamicDataSourceProvider {

    public static final String MASTER_NAME = "master";

    private static final String SPRING_DATASOURCE_URL = "spring.datasource.url";
    private static final String SPRING_DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String SPRING_DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final String SPRING_DATASOURCE_DRIVER = "spring.datasource.driver-class-name";
    /**
     * 显式 dynamic master 探测键：存在即由 Yml provider 供 master，本 Provider 让位.
     */
    private static final String DYNAMIC_MASTER_URL = "spring.datasource.dynamic.datasource.master.url";
    /** HikariCP 配置绑定前缀，用于 Binder.bind. */
    private static final String HIKARI_BIND_PREFIX = "spring.datasource.hikari";
    /** Druid 配置绑定前缀，用于 Binder.bind. */
    private static final String DRUID_BIND_PREFIX = "spring.datasource.druid";

    private final DefaultDataSourceCreator dataSourceCreator;
    private final ConfigurableEnvironment environment;

    @Override
    public Map<String, DataSource> loadDataSources() {
        String url = environment.getProperty(SPRING_DATASOURCE_URL);
        if (!StringUtils.hasText(url)) {
            return Collections.emptyMap();
        }
        // 显式 dynamic master 存在时跳过备用池创建：后加载的 Yml provider 会 putAll 覆盖本
        // provider 的 master，被覆盖的已启动池不在路由数据源的销毁清单里，连接与线程泄漏
        if (StringUtils.hasText(environment.getProperty(DYNAMIC_MASTER_URL))) {
            log.debug("已显式配置 dynamic master，跳过 spring.datasource.* 备用 master 创建");
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
        try {
            Binder.get(environment)
                    .bind(HIKARI_BIND_PREFIX, Bindable.of(HikariCpConfig.class))
                    .ifBound(property::setHikari);
        } catch (BindException e) {
            log.warn("Failed to bind Hikari connection pool properties (config may be malformed)", e);
        }
    }

    private void loadDruidProperties(DataSourceProperty property) {
        try {
            Binder.get(environment)
                    .bind(DRUID_BIND_PREFIX, Bindable.of(DruidConfig.class))
                    .ifBound(property::setDruid);
        } catch (BindException e) {
            log.warn("Failed to bind Druid connection pool properties (config may be malformed)", e);
        }
    }
}

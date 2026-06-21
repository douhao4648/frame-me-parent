package com.frame.me.tester.controller;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.tester.api.IDataSourceApi;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源切换演示 Controller.
 *
 * <p>通过 {@link DS} 注解演示在方法级别切换到 {@code second} 数据源。
 */
@RestController
@RequiredArgsConstructor
public class DataSourceController implements IDataSourceApi {

    private final DataSource dataSource;

    @Override
    public IResult<String> primary() {
        return Result.success(getDatabaseUrl());
    }

    @Override
    @DS("second")
    public IResult<String> second() {
        return Result.success(getDatabaseUrl());
    }

    @Override
    @DS("master")
    public IResult<String> master() {
        return Result.success(getDatabaseUrl());
    }

    @Override
    public IResult<Map<String, Map<String, Object>>> poolConfigs() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, DataSource> dataSources = ((DynamicRoutingDataSource) dataSource).getDataSources();

        dataSources.forEach((name, ds) -> {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("dataSourceType", ds.getClass().getName());
            try {
                DataSource realDataSource = unwrapDataSource(ds);
                if (realDataSource instanceof HikariDataSource hikari) {
                    config.put("poolType", "hikari");
                    config.put("poolName", hikari.getPoolName());
                    config.put("jdbcUrl", hikari.getJdbcUrl());
                    config.put("username", hikari.getUsername());
                    config.put("driverClassName", hikari.getDriverClassName());
                    config.put("maximumPoolSize", hikari.getMaximumPoolSize());
                    config.put("minimumIdle", hikari.getMinimumIdle());
                    config.put("connectionTimeout", hikari.getConnectionTimeout());
                    config.put("idleTimeout", hikari.getIdleTimeout());
                    config.put("maxLifetime", hikari.getMaxLifetime());
                    config.put("autoCommit", hikari.isAutoCommit());
                    config.put("validationTimeout", hikari.getValidationTimeout());
                    config.put("leakDetectionThreshold", hikari.getLeakDetectionThreshold());
                } else if (realDataSource instanceof DruidDataSource druid) {
                    config.put("poolType", "druid");
                    config.put("name", druid.getName());
                    config.put("url", druid.getUrl());
                    config.put("username", druid.getUsername());
                    config.put("driverClassName", druid.getDriverClassName());
                    config.put("initialSize", druid.getInitialSize());
                    config.put("maxActive", druid.getMaxActive());
                    config.put("minIdle", druid.getMinIdle());
                    config.put("maxWait", druid.getMaxWait());
                    config.put("testOnBorrow", druid.isTestOnBorrow());
                    config.put("testOnReturn", druid.isTestOnReturn());
                    config.put("testWhileIdle", druid.isTestWhileIdle());
                    config.put("validationQuery", druid.getValidationQuery());
                } else {
                    config.put("poolType", "unknown");
                }
            } catch (SQLException e) {
                config.put("error", e.getMessage());
            }
            result.put(name, config);
        });

        return Result.success(result);
    }

    private DataSource unwrapDataSource(DataSource dataSource) throws SQLException {
        if (dataSource instanceof com.baomidou.dynamic.datasource.ds.ItemDataSource item) {
            return item.getRealDataSource();
        }
        if (dataSource.isWrapperFor(HikariDataSource.class)) {
            return dataSource.unwrap(HikariDataSource.class);
        }
        if (dataSource.isWrapperFor(DruidDataSource.class)) {
            return dataSource.unwrap(DruidDataSource.class);
        }
        return dataSource;
    }

    private String getDatabaseUrl() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw new RuntimeException("获取数据库 URL 失败", e);
        }
    }
}

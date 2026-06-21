package com.frame.me.tester.controller;

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
    public IResult<Map<String, Map<String, Object>>> hikariConfigs() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, DataSource> dataSources = ((DynamicRoutingDataSource) dataSource).getDataSources();

        dataSources.forEach((name, ds) -> {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("dataSourceType", ds.getClass().getName());
            try {
                HikariDataSource hikari = unwrapHikariDataSource(ds);
                if (hikari == null) {
                    config.put("hikariDetected", false);
                } else {
                    config.put("hikariDetected", true);
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
                }
            } catch (SQLException e) {
                config.put("error", e.getMessage());
            }
            result.put(name, config);
        });

        return Result.success(result);
    }

    private HikariDataSource unwrapHikariDataSource(DataSource dataSource) throws SQLException {
        if (dataSource instanceof HikariDataSource) {
            return (HikariDataSource) dataSource;
        }
        if (dataSource.isWrapperFor(HikariDataSource.class)) {
            return dataSource.unwrap(HikariDataSource.class);
        }
        return null;
    }

    private String getDatabaseUrl() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw new RuntimeException("获取数据库 URL 失败", e);
        }
    }
}

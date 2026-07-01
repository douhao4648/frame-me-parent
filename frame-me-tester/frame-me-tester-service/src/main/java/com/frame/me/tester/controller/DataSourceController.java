//package com.frame.me.tester.controller;
//
//import com.frame.me.api.result.IResult;
//import com.frame.me.base.result.Result;
//import com.frame.me.tester.api.IDataSourceApi;
//import com.zaxxer.hikari.HikariDataSource;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.sql.DataSource;
//import java.lang.reflect.Method;
//import java.sql.Connection;
//import java.sql.SQLException;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
///**
// * 数据源切换演示 Controller.
// *
// * <p>兼容多种运行时组合：
// * <ul>
// *   <li>有/无 dynamic-datasource（多数据源切换与 poolConfigs 多节点展示仅在 dynamic-datasource 存在时生效）</li>
// *   <li>有/无 p6spy 装饰</li>
// *   <li>底层 HikariCP / Druid</li>
// * </ul>
// *
// * <p>对 dynamic-datasource 和 Druid 均使用反射访问，避免编译期强依赖。
// */
//@RestController
//@RequiredArgsConstructor
//public class DataSourceController implements IDataSourceApi {
//
//    private static final String DYNAMIC_ROUTING_DATA_SOURCE_CLASS = "com.baomidou.dynamic.datasource.DynamicRoutingDataSource";
//    private static final String DS_CONTEXT_HOLDER_CLASS = "com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder";
//    private static final String DRUID_DATA_SOURCE_CLASS = "com.alibaba.druid.pool.DruidDataSource";
//
//    private final DataSource dataSource;
//
//    @Override
//    public IResult<String> primary() {
//        return Result.success(getDatabaseUrl());
//    }
//
//    @Override
//    public IResult<String> second() {
//        return Result.success(executeWithDataSource("second", this::getDatabaseUrl));
//    }
//
//    @Override
//    public IResult<String> master() {
//        return Result.success(executeWithDataSource("master", this::getDatabaseUrl));
//    }
//
//    @Override
//    public IResult<Map<String, Map<String, Object>>> poolConfigs() {
//        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
//
//        try {
//            DataSource rootDataSource = unwrapToRealDataSource(dataSource);
//            Map<String, DataSource> dataSources = extractDataSources(rootDataSource);
//            if (dataSources == null || dataSources.isEmpty()) {
//                // 非 dynamic-datasource 场景：把整个 DataSource 当作一个节点展示
//                result.put("default", describeDataSource(dataSource));
//            } else {
//                dataSources.forEach((name, ds) -> result.put(name, describeDataSource(ds)));
//            }
//        } catch (Exception e) {
//            Map<String, Object> error = new LinkedHashMap<>();
//            error.put("error", "解析 DataSource 失败：" + e.getMessage());
//            result.put("error", error);
//        }
//        return Result.success(result);
//    }
//
//    /**
//     * 执行逻辑前切换 dynamic-datasource 数据源；若未引入 dynamic-datasource，则直接执行。
//     */
//    private String executeWithDataSource(String dataSourceName, java.util.function.Supplier<String> action) {
//        if (!isDynamicDataSourcePresent()) {
//            // 未引入 dynamic-datasource，无法切换，直接执行
//            return action.get();
//        }
//        try {
//            Class<?> holderClass = Class.forName(DS_CONTEXT_HOLDER_CLASS);
//            Method push = holderClass.getMethod("push", String.class);
//            Method poll = holderClass.getMethod("poll");
//            push.invoke(null, dataSourceName);
//            try {
//                return action.get();
//            } finally {
//                poll.invoke(null);
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("切换数据源 [" + dataSourceName + "] 失败", e);
//        }
//    }
//
//    /**
//     * 若顶层是 DynamicRoutingDataSource，返回其管理的所有数据源；否则返回 null。
//     */
//    @SuppressWarnings("unchecked")
//    private Map<String, DataSource> extractDataSources(DataSource dataSource) {
//        if (!isDynamicRoutingDataSource(dataSource)) {
//            return null;
//        }
//        try {
//            Method method = dataSource.getClass().getMethod("getDataSources");
//            Object result = method.invoke(dataSource);
//            if (result instanceof Map) {
//                return (Map<String, DataSource>) result;
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("获取 dynamic-datasource 数据源列表失败", e);
//        }
//        return null;
//    }
//
//    /**
//     * 描述单个数据源，支持被 p6spy、dynamic-datasource 等包装的情况。
//     */
//    private Map<String, Object> describeDataSource(DataSource dataSource) {
//        Map<String, Object> config = new LinkedHashMap<>();
//        config.put("dataSourceType", dataSource.getClass().getName());
//        try {
//            DataSource realDataSource = unwrapToRealDataSource(dataSource);
//            config.put("realDataSourceType", realDataSource.getClass().getName());
//            if (realDataSource instanceof HikariDataSource hikari) {
//                fillHikariConfig(config, hikari);
//            } else if (isDruidDataSource(realDataSource)) {
//                fillDruidConfig(config, realDataSource);
//            } else {
//                config.put("poolType", "unknown");
//            }
//        } catch (Exception e) {
//            config.put("error", e.getMessage());
//        }
//        return config;
//    }
//
//    private void fillHikariConfig(Map<String, Object> config, HikariDataSource hikari) {
//        config.put("poolType", "hikari");
//        config.put("poolName", hikari.getPoolName());
//        config.put("jdbcUrl", hikari.getJdbcUrl());
//        config.put("username", hikari.getUsername());
//        config.put("driverClassName", hikari.getDriverClassName());
//        config.put("maximumPoolSize", hikari.getMaximumPoolSize());
//        config.put("minimumIdle", hikari.getMinimumIdle());
//        config.put("connectionTimeout", hikari.getConnectionTimeout());
//        config.put("idleTimeout", hikari.getIdleTimeout());
//        config.put("maxLifetime", hikari.getMaxLifetime());
//        config.put("autoCommit", hikari.isAutoCommit());
//        config.put("validationTimeout", hikari.getValidationTimeout());
//        config.put("leakDetectionThreshold", hikari.getLeakDetectionThreshold());
//    }
//
//    private void fillDruidConfig(Map<String, Object> config, Object druid) {
//        config.put("poolType", "druid");
//        config.put("name", invokeGetter(druid, "getName"));
//        config.put("url", invokeGetter(druid, "getUrl"));
//        config.put("username", invokeGetter(druid, "getUsername"));
//        config.put("driverClassName", invokeGetter(druid, "getDriverClassName"));
//        config.put("initialSize", invokeGetter(druid, "getInitialSize"));
//        config.put("maxActive", invokeGetter(druid, "getMaxActive"));
//        config.put("minIdle", invokeGetter(druid, "getMinIdle"));
//        config.put("maxWait", invokeGetter(druid, "getMaxWait"));
//        config.put("testOnBorrow", invokeGetter(druid, "isTestOnBorrow"));
//        config.put("testOnReturn", invokeGetter(druid, "isTestOnReturn"));
//        config.put("testWhileIdle", invokeGetter(druid, "isTestWhileIdle"));
//        config.put("validationQuery", invokeGetter(druid, "getValidationQuery"));
//    }
//
//    private boolean isDynamicDataSourcePresent() {
//        try {
//            Class.forName(DS_CONTEXT_HOLDER_CLASS);
//            return true;
//        } catch (ClassNotFoundException e) {
//            return false;
//        }
//    }
//
//    private boolean isDynamicRoutingDataSource(DataSource dataSource) {
//        return DYNAMIC_ROUTING_DATA_SOURCE_CLASS.equals(dataSource.getClass().getName());
//    }
//
//    private boolean isDruidDataSource(DataSource dataSource) {
//        return DRUID_DATA_SOURCE_CLASS.equals(dataSource.getClass().getName());
//    }
//
//    private Object invokeGetter(Object target, String methodName) {
//        try {
//            Method method = target.getClass().getMethod(methodName);
//            return method.invoke(target);
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    /**
//     * 递归解包数据源，处理 dynamic-datasource、p6spy 等装饰器。
//     */
//    private DataSource unwrapToRealDataSource(DataSource dataSource) throws SQLException {
//        DataSource current = dataSource;
//        for (int i = 0; i < 10; i++) {
//            if (current == null) {
//                break;
//            }
//            if (isDynamicRoutingDataSource(current)) {
//                return current;
//            }
//            if (current instanceof HikariDataSource || isDruidDataSource(current)) {
//                return current;
//            }
//
//            // 1. 先尝试 JDBC 标准 unwrap
//            DataSource unwrapped = tryUnwrapToKnownType(current);
//            if (unwrapped != null && unwrapped != current) {
//                current = unwrapped;
//                continue;
//            }
//
//            // 2. 反射调用 getRealDataSource()（兼容 p6spy、dynamic-datasource ItemDataSource）
//            DataSource next = invokeGetRealDataSource(current);
//            if (next != null && next != current) {
//                current = next;
//                continue;
//            }
//
//            // 无法继续解包
//            break;
//        }
//        return current;
//    }
//
//    private DataSource tryUnwrapToKnownType(DataSource dataSource) throws SQLException {
//        // dynamic-datasource
//        try {
//            Class<?> routingClass = Class.forName(DYNAMIC_ROUTING_DATA_SOURCE_CLASS);
//            if (dataSource.isWrapperFor(routingClass.asSubclass(DataSource.class))) {
//                return dataSource.unwrap(routingClass.asSubclass(DataSource.class));
//            }
//        } catch (ClassNotFoundException e) {
//            // 未引入 dynamic-datasource，忽略
//        }
//
//        // HikariCP
//        DataSource result = tryUnwrap(dataSource, HikariDataSource.class);
//        if (result != null) {
//            return result;
//        }
//
//        // Druid
//        try {
//            Class<?> druidClass = Class.forName(DRUID_DATA_SOURCE_CLASS);
//            if (dataSource.isWrapperFor(druidClass.asSubclass(DataSource.class))) {
//                return dataSource.unwrap(druidClass.asSubclass(DataSource.class));
//            }
//        } catch (ClassNotFoundException e) {
//            // 未引入 Druid，忽略
//        }
//        return null;
//    }
//
//    private <T extends DataSource> T tryUnwrap(DataSource dataSource, Class<T> targetType) {
//        try {
//            if (dataSource.isWrapperFor(targetType)) {
//                return dataSource.unwrap(targetType);
//            }
//        } catch (SQLException | UnsupportedOperationException e) {
//            // 某些包装器没有正确实现 unwrap，忽略并继续尝试反射
//        }
//        return null;
//    }
//
//    private DataSource invokeGetRealDataSource(DataSource dataSource) {
//        try {
//            Method method = dataSource.getClass().getMethod("getRealDataSource");
//            Object result = method.invoke(dataSource);
//            if (result instanceof DataSource ds) {
//                return ds;
//            }
//        } catch (NoSuchMethodException e) {
//            // 当前类没有 getRealDataSource 方法，正常
//        } catch (Exception e) {
//            // 反射调用失败，返回 null 让外层终止解包
//        }
//        return null;
//    }
//
//    private String getDatabaseUrl() {
//        try (Connection connection = dataSource.getConnection()) {
//            return connection.getMetaData().getURL();
//        } catch (SQLException e) {
//            throw new RuntimeException("获取数据库 URL 失败", e);
//        }
//    }
//}

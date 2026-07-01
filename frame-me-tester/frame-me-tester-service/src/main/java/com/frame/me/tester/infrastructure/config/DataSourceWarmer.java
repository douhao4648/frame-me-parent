//package com.frame.me.tester.infrastructure.config;
//
//import com.mybatisflex.core.datasource.DataSourceKey;
//import com.mybatisflex.core.datasource.FlexDataSource;
//import com.mybatisflex.spring.boot.v4.MybatisFlexProperties;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//import javax.sql.DataSource;
//import java.sql.Connection;
//import java.sql.SQLException;
//import java.sql.Statement;
//import java.sql.Wrapper;
//import java.util.Map;
//
///**
// * 多数据源预热.
// *
// * <p>mybatis-flex 的 {@link FlexDataSource} 内部持有多个真实 {@link DataSource}，
// * 而 HikariCP 默认在第一次 {@link DataSource#getConnection()} 时才初始化连接池。
// * 这导致 second 等未被访问的数据源在启动后不会触发 {@code SELECT 2} 等验证 SQL，
// * p6spy 也不会打印。</p>
// *
// * <p>本类在应用启动后遍历所有内部数据源，先切换 {@link DataSourceKey} 再通过外层
// * （可能被 p6spy 包装的）{@code DataSource} 拿连接，并显式执行配置中的
// * {@code connection-test-query}，使 p6spy 能正常打印各自的验证 SQL。</p>
// */
//@Slf4j
//@Component
//public class DataSourceWarmer implements CommandLineRunner {
//
//    private final DataSource dataSource;
//    private final MybatisFlexProperties mybatisFlexProperties;
//
//    public DataSourceWarmer(DataSource dataSource, MybatisFlexProperties mybatisFlexProperties) {
//        this.dataSource = dataSource;
//        this.mybatisFlexProperties = mybatisFlexProperties;
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//        FlexDataSource flexDataSource = unwrapFlexDataSource(dataSource);
//        if (flexDataSource == null) {
//            log.debug("DataSource is not FlexDataSource, skip warming");
//            return;
//        }
//
//        Map<String, DataSource> dataSourceMap = flexDataSource.getDataSourceMap();
//        for (String key : dataSourceMap.keySet()) {
//            String testQuery = getConnectionTestQuery(key);
//            // 通过 DataSourceKey 切换到指定数据源，再用外层被 p6spy 包装的 DataSource 拿连接，
//            // 这样 p6spy 才能捕获到执行的 connection-test-query。
//            DataSourceKey.use(key, () -> {
//                try (Connection conn = dataSource.getConnection()) {
//                    if (testQuery != null && !testQuery.isEmpty()) {
//                        try (Statement stmt = conn.createStatement()) {
//                            stmt.execute(testQuery);
//                        }
//                    }
//                    log.info("Preheated datasource: {}", key);
//                } catch (SQLException e) {
//                    throw new RuntimeException("Failed to preheat datasource: " + key, e);
//                }
//            });
//        }
//    }
//
//    /**
//     * 读取指定数据源配置的 {@code connection-test-query}.
//     */
//    private String getConnectionTestQuery(String key) {
//        Map<String, Map<String, String>> datasource = mybatisFlexProperties.getDatasource();
//        if (datasource == null) {
//            return null;
//        }
//        Map<String, String> props = datasource.get(key);
//        if (props == null) {
//            return null;
//        }
//        return props.get("connection-test-query");
//    }
//
//    /**
//     * 解包出底层的 {@link FlexDataSource}，兼容 p6spy 等装饰器包装的场景.
//     */
//    private FlexDataSource unwrapFlexDataSource(DataSource dataSource) {
//        if (dataSource instanceof FlexDataSource flex) {
//            return flex;
//        }
//        if (dataSource instanceof Wrapper wrapper) {
//            try {
//                if (wrapper.isWrapperFor(FlexDataSource.class)) {
//                    return wrapper.unwrap(FlexDataSource.class);
//                }
//            } catch (SQLException e) {
//                log.debug("Failed to unwrap DataSource to FlexDataSource", e);
//            }
//        }
//        return null;
//    }
//
//}

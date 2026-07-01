package com.frame.me.tester.flex;

import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.service.IFlexDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Flex 原生多数据源集成测试.
 *
 * <p>用单个 MySQL 容器承载两个库：{@code frame_me_test}（master）与 {@code frame_me_test_2}（second）。
 * 通过 {@code mybatis-flex.datasource.*} 配置两库、{@code @UseDataSource("second")} 切换，
 * 验证默认操作落 master、second 操作落 second，直连各物理库统计条数断言路由正确。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("isDockerAvailable")
class FlexMultiDataSourceTest {

    private static final String DB_MASTER = "frame_me_test";
    private static final String DB_SECOND = "frame_me_test_2";

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS flex_demo (
                id BIGINT PRIMARY KEY,
                name VARCHAR(100),
                age INT,
                create_time DATETIME,
                update_time DATETIME,
                deleted INT DEFAULT 0,
                version INT DEFAULT 1
            )""";

    private static final boolean DOCKER_AVAILABLE;
    private static final MySQLContainer<?> MYSQL;

    static {
        boolean docker;
        try {
            DockerClientFactory.instance().client();
            docker = true;
        } catch (Exception e) {
            docker = false;
        }
        DOCKER_AVAILABLE = docker;
        if (docker) {
            MYSQL = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName(DB_MASTER)
                    .withUsername("test")
                    .withPassword("test");
            MYSQL.start();
            prepareDatabases();
        } else {
            MYSQL = null;
        }
    }

    static boolean isDockerAvailable() {
        return DOCKER_AVAILABLE;
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT)
                + "/" + database + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    /**
     * 用 root 创建 second 库并授权 test 用户，随后在两个库各建 {@code flex_demo} 表.
     */
    private static void prepareDatabases() {
        String rootUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT)
                + "/?allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection c = DriverManager.getConnection(rootUrl, "root", MYSQL.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + DB_SECOND);
            st.execute("GRANT ALL PRIVILEGES ON " + DB_SECOND + ".* TO 'test'@'%'");
            st.execute("FLUSH PRIVILEGES");
        } catch (SQLException e) {
            throw new IllegalStateException("准备 second 数据库失败", e);
        }
        for (String db : new String[]{DB_MASTER, DB_SECOND}) {
            try (Connection c = DriverManager.getConnection(jdbcUrl(db), "test", "test");
                 Statement st = c.createStatement()) {
                st.execute(DDL);
            } catch (SQLException e) {
                throw new IllegalStateException("在库 " + db + " 建表失败", e);
            }
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("mybatis-flex.datasource.master.url", () -> jdbcUrl(DB_MASTER));
        registry.add("mybatis-flex.datasource.master.username", () -> "test");
        registry.add("mybatis-flex.datasource.master.password", () -> "test");
        registry.add("mybatis-flex.datasource.master.driver-class-name", MYSQL::getDriverClassName);
        registry.add("mybatis-flex.datasource.second.url", () -> jdbcUrl(DB_SECOND));
        registry.add("mybatis-flex.datasource.second.username", () -> "test");
        registry.add("mybatis-flex.datasource.second.password", () -> "test");
        registry.add("mybatis-flex.datasource.second.driver-class-name", MYSQL::getDriverClassName);
        registry.add("mybatis-flex.default-datasource-key", () -> "master");
        registry.add("spring.sql.init.mode", () -> "never");
        // 让 flex 主键走 base 的 SnowflakeUtils
        registry.add("me.snowflake.worker-id", () -> "5");
    }

    @Autowired
    private IFlexDemoService flexDemoService;

    @BeforeEach
    void cleanTables() {
        for (String db : new String[]{DB_MASTER, DB_SECOND}) {
            try (Connection c = DriverManager.getConnection(jdbcUrl(db), "test", "test");
                 Statement st = c.createStatement()) {
                st.execute("TRUNCATE TABLE flex_demo");
            } catch (SQLException e) {
                throw new IllegalStateException("清理库 " + db + " 失败", e);
            }
        }
    }

    @Test
    @DisplayName("默认操作写入 master 数据源")
    void create_should_write_to_master() {
        Long id = flexDemoService.create(dto("master-张三", 20));

        assertThat(id).isNotNull();
        assertThat(countIn(DB_MASTER)).isEqualTo(1);
        assertThat(countIn(DB_SECOND)).isZero();
        assertThat(flexDemoService.count()).isEqualTo(1);
        assertThat(flexDemoService.countInSecond()).isZero();
    }

    @Test
    @DisplayName("@DS(\"second\") 操作写入 second 数据源")
    void createInSecond_should_write_to_second() {
        Long id = flexDemoService.createInSecond(dto("second-李四", 30));

        assertThat(id).isNotNull();
        assertThat(countIn(DB_SECOND)).isEqualTo(1);
        assertThat(countIn(DB_MASTER)).isZero();
        assertThat(flexDemoService.countInSecond()).isEqualTo(1);
        assertThat(flexDemoService.count()).isZero();
    }

    /**
     * 直连指定物理库统计未删除记录数，绕过 dynamic-datasource 路由以验证数据真实落库位置.
     */
    private long countIn(String database) {
        try (Connection c = DriverManager.getConnection(jdbcUrl(database), "test", "test");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM flex_demo WHERE deleted = 0")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("统计库 " + database + " 失败", e);
        }
    }

    private static FlexDemoDTO dto(String name, int age) {
        FlexDemoDTO dto = new FlexDemoDTO();
        dto.setName(name);
        dto.setAge(age);
        return dto;
    }
}

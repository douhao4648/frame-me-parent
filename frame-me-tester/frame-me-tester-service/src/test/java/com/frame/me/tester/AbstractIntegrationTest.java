package com.frame.me.tester;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers + MySQL 集成测试基类.
 *
 * <p>所有需要真实数据库的集成测试直接继承此类，即可获得：
 * <ul>
 *     <li>JVM 级别单例的 MySQL 容器</li>
 *     <li>动态注入的 DataSource 配置</li>
 *     <li>Spring Boot 测试上下文</li>
 * </ul>
 *
 * <p>若本地 Docker 不可用，测试将自动跳过（通过 {@link org.junit.jupiter.api.Assumptions} 在基类中统一处理）。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = "me.mybatis.meta-object-handler.enabled=true")
public abstract class AbstractIntegrationTest {

    /**
     * Docker 是否可用.
     */
    protected static final boolean DOCKER_AVAILABLE;

    /**
     * 静态 MySQL 容器，JVM 级别复用.
     */
    protected static final MySQLContainer<?> MYSQL;

    static {
        boolean dockerAvailable;
        try {
            DockerClientFactory.instance().client();
            dockerAvailable = true;
        } catch (Exception e) {
            dockerAvailable = false;
        }
        DOCKER_AVAILABLE = dockerAvailable;

        if (dockerAvailable) {
            MYSQL = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("frame_me_test")
                    .withUsername("test")
                    .withPassword("test");
            MYSQL.start();
        } else {
            MYSQL = null;
        }
    }

    /**
     * 将容器连接信息动态注入 Spring Environment，覆盖 application.yml 中的配置.
     * <p>若 Docker 不可用，不注入任何属性，让 Spring 使用 application.yml 中的默认配置（测试会被跳过）。
     */
    @DynamicPropertySource
    static void registerMySQLProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // 禁用 spring.sql.init，避免业务 application.yml 中的 always 干扰测试自身的 @Sql 初始化
        registry.add("spring.sql.init.mode", () -> "never");
    }
}

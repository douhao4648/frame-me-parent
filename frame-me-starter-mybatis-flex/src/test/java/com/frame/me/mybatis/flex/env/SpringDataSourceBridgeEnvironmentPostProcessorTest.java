package com.frame.me.mybatis.flex.env;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SpringDataSourceBridgeEnvironmentPostProcessor} 桥接行为自校验.
 */
class SpringDataSourceBridgeEnvironmentPostProcessorTest {

    private final SpringDataSourceBridgeEnvironmentPostProcessor processor =
            new SpringDataSourceBridgeEnvironmentPostProcessor();

    private StandardEnvironment environmentWith(Map<String, Object> props) {
        StandardEnvironment environment = new StandardEnvironment();
        // 置于首位，模拟 application.yml 优先级高于桥接属性源
        environment.getPropertySources().addFirst(new MapPropertySource("test", props));
        return environment;
    }

    @Test
    void bridgesSpringDataSourceAsFlexMaster() {
        StandardEnvironment environment = environmentWith(Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:3306/frame_me_test",
                "spring.datasource.username", "root",
                "spring.datasource.password", "ME(secret)",
                "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver",
                "spring.datasource.hikari.maximum-pool-size", "10",
                "spring.datasource.druid.initial-size", "3"));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("mybatis-flex.datasource.master.url"))
                .isEqualTo("jdbc:mysql://localhost:3306/frame_me_test");
        assertThat(environment.getProperty("mybatis-flex.datasource.master.username")).isEqualTo("root");
        assertThat(environment.getProperty("mybatis-flex.datasource.master.password")).isEqualTo("ME(secret)");
        assertThat(environment.getProperty("mybatis-flex.datasource.master.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(environment.getProperty("mybatis-flex.datasource.master.maximum-pool-size")).isEqualTo("10");
        assertThat(environment.getProperty("mybatis-flex.datasource.master.initial-size")).isEqualTo("3");
        assertThat(environment.getPropertySources().get(
                SpringDataSourceBridgeEnvironmentPostProcessor.BRIDGE_PROPERTY_SOURCE_NAME)).isNotNull();
    }

    @Test
    void backsOffWhenFlexMasterExplicitlyConfigured() {
        StandardEnvironment environment = environmentWith(Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:3306/a",
                "mybatis-flex.datasource.master.url", "jdbc:mysql://localhost:3306/b"));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().get(
                SpringDataSourceBridgeEnvironmentPostProcessor.BRIDGE_PROPERTY_SOURCE_NAME)).isNull();
        assertThat(environment.getProperty("mybatis-flex.datasource.master.url"))
                .isEqualTo("jdbc:mysql://localhost:3306/b");
    }

    @Test
    void explicitMasterPropertyOverridesBridgePerProperty() {
        StandardEnvironment environment = environmentWith(Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:3306/a",
                "mybatis-flex.datasource.master.type", "druid"));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("mybatis-flex.datasource.master.url"))
                .isEqualTo("jdbc:mysql://localhost:3306/a");
        assertThat(environment.getProperty("mybatis-flex.datasource.master.type")).isEqualTo("druid");
    }

    @Test
    void mapsExplicitSpringDataSourceTypeToFlexAlias() {
        StandardEnvironment environment = environmentWith(Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:3306/a",
                "spring.datasource.type", "com.alibaba.druid.pool.DruidDataSource"));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("mybatis-flex.datasource.master.type")).isEqualTo("druid");
    }

    @Test
    void infersTypeFromSinglePoolPropertyPrefix() {
        StandardEnvironment environment = environmentWith(Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:3306/a",
                "spring.datasource.druid.initial-size", "3"));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("mybatis-flex.datasource.master.type")).isEqualTo("druid");
    }

    @Test
    void skipsWhenNoSpringDataSourceOrDisabled() {
        StandardEnvironment empty = environmentWith(Map.of());
        processor.postProcessEnvironment(empty, null);
        assertThat(empty.getPropertySources().get(
                SpringDataSourceBridgeEnvironmentPostProcessor.BRIDGE_PROPERTY_SOURCE_NAME)).isNull();

        StandardEnvironment disabled = environmentWith(Map.of(
                "spring.datasource.url", "jdbc:mysql://localhost:3306/a",
                "me.mybatis.datasource-bridge.enabled", "false"));
        processor.postProcessEnvironment(disabled, null);
        assertThat(disabled.getPropertySources().get(
                SpringDataSourceBridgeEnvironmentPostProcessor.BRIDGE_PROPERTY_SOURCE_NAME)).isNull();
    }
}

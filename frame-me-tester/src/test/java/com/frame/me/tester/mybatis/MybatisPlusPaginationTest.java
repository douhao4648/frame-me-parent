package com.frame.me.tester.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.tester.AbstractIntegrationTest;
import com.frame.me.tester.entity.DemoEntity;
import com.frame.me.tester.mapper.DemoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus 分页插件集成测试.
 *
 * <p>需要本地 Docker 环境，否则测试会被跳过。
 */
@EnabledIf("isDockerAvailable")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MybatisPlusPaginationTest extends AbstractIntegrationTest {

    @Autowired
    private DemoMapper demoMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("TRUNCATE TABLE demo_user");
        for (int i = 1; i <= 25; i++) {
            DemoEntity entity = new DemoEntity();
            entity.setName("用户" + i);
            entity.setAge(20 + i);
            demoMapper.insert(entity);
        }
    }

    @Test
    @DisplayName("分页查询第一页，每页 10 条")
    void testPaginationFirstPage() {
        Page<DemoEntity> page = new Page<>(1, 10);
        Page<DemoEntity> result = demoMapper.selectPage(page, null);

        assertThat(result.getRecords()).hasSize(10);
        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getCurrent()).isEqualTo(1);
    }

    @Test
    @DisplayName("分页查询第二页，每页 10 条")
    void testPaginationSecondPage() {
        Page<DemoEntity> page = new Page<>(2, 10);
        Page<DemoEntity> result = demoMapper.selectPage(page, null);

        assertThat(result.getRecords()).hasSize(10);
        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getCurrent()).isEqualTo(2);
    }

    @Test
    @DisplayName("分页查询第三页，剩余 5 条")
    void testPaginationLastPage() {
        Page<DemoEntity> page = new Page<>(3, 10);
        Page<DemoEntity> result = demoMapper.selectPage(page, null);

        assertThat(result.getRecords()).hasSize(5);
        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getCurrent()).isEqualTo(3);
    }

    @Test
    @DisplayName("分页 + 条件查询")
    void testPaginationWithCondition() {
        QueryWrapper<DemoEntity> wrapper = new QueryWrapper<>();
        wrapper.ge("age", 30);

        Page<DemoEntity> page = new Page<>(1, 10);
        Page<DemoEntity> result = demoMapper.selectPage(page, wrapper);

        // age >= 30 的数据有 16 条 (age 从 21 到 45)
        assertThat(result.getRecords()).hasSize(10);
        assertThat(result.getTotal()).isEqualTo(16);
        assertThat(result.getPages()).isEqualTo(2);

        for (DemoEntity record : result.getRecords()) {
            assertThat(record.getAge()).isGreaterThanOrEqualTo(30);
        }
    }
}

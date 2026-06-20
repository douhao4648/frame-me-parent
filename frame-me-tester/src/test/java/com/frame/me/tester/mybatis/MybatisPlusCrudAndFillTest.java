package com.frame.me.tester.mybatis;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus CRUD 与自动填充集成测试.
 *
 * <p>需要本地 Docker 环境，否则测试会被跳过。
 */
@EnabledIf("isDockerAvailable")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MybatisPlusCrudAndFillTest extends AbstractIntegrationTest {

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
    }

    @Test
    @DisplayName("插入数据时自动填充 createTime、updateTime、deleted、version")
    void testInsertAutoFill() {
        DemoEntity entity = new DemoEntity();
        entity.setName("张三");
        entity.setAge(25);

        int rows = demoMapper.insert(entity);
        assertThat(rows).isEqualTo(1);
        assertThat(entity.getId()).isNotNull();

        DemoEntity saved = demoMapper.selectById(entity.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo("张三");
        assertThat(saved.getAge()).isEqualTo(25);
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved.getDeleted()).isEqualTo(0);
        assertThat(saved.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("根据 ID 查询数据")
    void testSelectById() {
        DemoEntity entity = new DemoEntity();
        entity.setName("李四");
        entity.setAge(30);
        demoMapper.insert(entity);

        DemoEntity found = demoMapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("查询全部数据列表")
    void testSelectList() {
        DemoEntity e1 = new DemoEntity();
        e1.setName("用户A");
        e1.setAge(20);
        demoMapper.insert(e1);

        DemoEntity e2 = new DemoEntity();
        e2.setName("用户B");
        e2.setAge(22);
        demoMapper.insert(e2);

        List<DemoEntity> list = demoMapper.selectList(null);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("更新数据时自动填充 updateTime")
    void testUpdateAutoFill() throws InterruptedException {
        DemoEntity entity = new DemoEntity();
        entity.setName("王五");
        entity.setAge(28);
        demoMapper.insert(entity);

        DemoEntity before = demoMapper.selectById(entity.getId());

        // 暂停 1 秒，确保 updateTime 严格晚于 insert 时的 updateTime
        Thread.sleep(1000);

        DemoEntity update = new DemoEntity();
        update.setId(entity.getId());
        update.setName("王五-更新");
        update.setAge(29);
        update.setVersion(before.getVersion());

        int rows = demoMapper.updateById(update);
        assertThat(rows).isEqualTo(1);

        DemoEntity after = demoMapper.selectById(entity.getId());
        assertThat(after.getName()).isEqualTo("王五-更新");
        assertThat(after.getAge()).isEqualTo(29);
        assertThat(after.getUpdateTime()).isAfter(before.getUpdateTime());
    }

    @Test
    @DisplayName("根据 ID 删除数据（逻辑删除）")
    void testDeleteById() {
        DemoEntity entity = new DemoEntity();
        entity.setName("赵六");
        entity.setAge(35);
        demoMapper.insert(entity);

        int rows = demoMapper.deleteById(entity.getId());
        assertThat(rows).isEqualTo(1);

        DemoEntity found = demoMapper.selectById(entity.getId());
        assertThat(found).isNull();
    }
}

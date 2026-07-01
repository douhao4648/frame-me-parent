//package com.frame.me.tester.mybatis;
//
//import com.frame.me.tester.AbstractIntegrationTest;
//import com.frame.me.tester.entity.DemoEntity;
//import com.frame.me.tester.mapper.DemoMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.condition.EnabledIf;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.test.context.jdbc.Sql;
//import org.testcontainers.DockerClientFactory;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * MyBatis-Plus 乐观锁集成测试.
// *
// * <p>需要本地 Docker 环境，否则测试会被跳过。
// */
//@EnabledIf("isDockerAvailable")
//@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
//class MybatisPlusOptimisticLockTest extends AbstractIntegrationTest {
//
//    @Autowired
//    private DemoMapper demoMapper;
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    static boolean isDockerAvailable() {
//        try {
//            DockerClientFactory.instance().client();
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    @BeforeEach
//    void setUp() {
//        jdbcTemplate.update("TRUNCATE TABLE demo_user");
//    }
//
//    @Test
//    @DisplayName("更新成功后 version 自动递增")
//    void testVersionIncrementOnUpdate() {
//        DemoEntity entity = new DemoEntity();
//        entity.setName("乐观锁测试");
//        entity.setAge(20);
//        demoMapper.insert(entity);
//
//        assertThat(entity.getVersion()).isEqualTo(1);
//
//        DemoEntity update = new DemoEntity();
//        update.setId(entity.getId());
//        update.setName("乐观锁测试-更新");
//        update.setAge(21);
//        update.setVersion(1);
//
//        int rows = demoMapper.updateById(update);
//        assertThat(rows).isEqualTo(1);
//
//        DemoEntity after = demoMapper.selectById(entity.getId());
//        assertThat(after.getVersion()).isEqualTo(2);
//        assertThat(after.getName()).isEqualTo("乐观锁测试-更新");
//    }
//
//    @Test
//    @DisplayName("使用旧 version 更新失败（乐观锁冲突）")
//    void testOptimisticLockConflict() {
//        DemoEntity entity = new DemoEntity();
//        entity.setName("冲突测试");
//        entity.setAge(30);
//        demoMapper.insert(entity);
//
//        // 模拟另一个线程已更新，version 变为 2
//        jdbcTemplate.update("UPDATE demo_user SET version = 2, name = '已被更新' WHERE id = ?", entity.getId());
//
//        // 使用旧 version = 1 尝试更新
//        DemoEntity update = new DemoEntity();
//        update.setId(entity.getId());
//        update.setName("冲突测试-更新");
//        update.setAge(31);
//        update.setVersion(1);
//
//        int rows = demoMapper.updateById(update);
//        assertThat(rows).isEqualTo(0);
//
//        DemoEntity after = demoMapper.selectById(entity.getId());
//        assertThat(after.getName()).isEqualTo("已被更新");
//        assertThat(after.getVersion()).isEqualTo(2);
//    }
//
//    @Test
//    @DisplayName("多次更新后 version 持续递增")
//    void testVersionMultipleUpdates() {
//        DemoEntity entity = new DemoEntity();
//        entity.setName("多次更新");
//        entity.setAge(20);
//        demoMapper.insert(entity);
//        Long id = entity.getId();
//
//        for (int i = 1; i <= 5; i++) {
//            DemoEntity current = demoMapper.selectById(id);
//
//            DemoEntity update = new DemoEntity();
//            update.setId(id);
//            update.setName("多次更新-" + i);
//            update.setAge(20 + i);
//            update.setVersion(current.getVersion());
//
//            int rows = demoMapper.updateById(update);
//            assertThat(rows).isEqualTo(1);
//        }
//
//        DemoEntity finalEntity = demoMapper.selectById(id);
//        assertThat(finalEntity.getVersion()).isEqualTo(6);
//        assertThat(finalEntity.getName()).isEqualTo("多次更新-5");
//        assertThat(finalEntity.getAge()).isEqualTo(25);
//    }
//}

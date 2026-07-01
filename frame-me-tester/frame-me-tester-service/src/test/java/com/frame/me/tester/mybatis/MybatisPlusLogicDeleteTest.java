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
// * MyBatis-Plus 逻辑删除集成测试.
// *
// * <p>需要本地 Docker 环境，否则测试会被跳过。
// */
//@EnabledIf("isDockerAvailable")
//@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
//class MybatisPlusLogicDeleteTest extends AbstractIntegrationTest {
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
//    @DisplayName("删除后 selectById 返回 null（逻辑删除）")
//    void testLogicDeleteSelectById() {
//        DemoEntity entity = new DemoEntity();
//        entity.setName("逻辑删除测试");
//        entity.setAge(20);
//        demoMapper.insert(entity);
//
//        demoMapper.deleteById(entity.getId());
//
//        DemoEntity found = demoMapper.selectById(entity.getId());
//        assertThat(found).isNull();
//    }
//
//    @Test
//    @DisplayName("删除后 selectList 不返回已删除数据")
//    void testLogicDeleteSelectList() {
//        DemoEntity e1 = new DemoEntity();
//        e1.setName("保留用户");
//        e1.setAge(20);
//        demoMapper.insert(e1);
//
//        DemoEntity e2 = new DemoEntity();
//        e2.setName("删除用户");
//        e2.setAge(22);
//        demoMapper.insert(e2);
//
//        demoMapper.deleteById(e2.getId());
//
//        var list = demoMapper.selectList(null);
//        assertThat(list).hasSize(1);
//        assertThat(list.get(0).getName()).isEqualTo("保留用户");
//    }
//
//    @Test
//    @DisplayName("数据库中 deleted 字段被更新为 1")
//    void testLogicDeleteFieldValue() {
//        DemoEntity entity = new DemoEntity();
//        entity.setName("字段验证");
//        entity.setAge(25);
//        demoMapper.insert(entity);
//
//        demoMapper.deleteById(entity.getId());
//
//        Integer deleted = jdbcTemplate.queryForObject("SELECT deleted FROM demo_user WHERE id = ?", Integer.class, entity.getId());
//        assertThat(deleted).isEqualTo(1);
//    }
//}

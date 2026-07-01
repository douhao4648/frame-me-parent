//package com.frame.me.tester.mybatis;
//
//import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.frame.me.tester.AbstractIntegrationTest;
//import com.frame.me.tester.entity.DemoEntity;
//import com.frame.me.tester.mapper.DemoMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.condition.EnabledIf;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.test.context.jdbc.Sql;
//import org.springframework.transaction.annotation.Transactional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * {@link DemoMapper} Testcontainers 集成测试.
// *
// * <p>覆盖 MyBatis-Plus 核心能力：自动填充、查询、乐观锁、逻辑删除、分页。
// */
//@EnabledIf("isDockerAvailable")
//@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
//@Transactional
//class DemoMapperIntegrationTest extends AbstractIntegrationTest {
//
//    @Autowired
//    private DemoMapper demoMapper;
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    static boolean isDockerAvailable() {
//        return DOCKER_AVAILABLE;
//    }
//
//    @BeforeEach
//    void setUp() {
//        jdbcTemplate.update("TRUNCATE TABLE demo_user");
//    }
//
//    private DemoEntity saveDemoEntity(String name, int age) {
//        DemoEntity entity = new DemoEntity();
//        entity.setName(name);
//        entity.setAge(age);
//        demoMapper.insert(entity);
//        return entity;
//    }
//
//    @Test
//    void shouldInsertAndAutoFillFields() {
//        DemoEntity entity = new DemoEntity();
//        entity.setName("Alice");
//        entity.setAge(30);
//
//        int rows = demoMapper.insert(entity);
//
//        assertThat(rows).isEqualTo(1);
//        assertThat(entity.getId()).isNotNull();
//        assertThat(entity.getCreateTime()).isNotNull();
//        assertThat(entity.getUpdateTime()).isNotNull();
//        assertThat(entity.getDeleted()).isEqualTo(0);
//        assertThat(entity.getVersion()).isEqualTo(1);
//    }
//
//    @Test
//    void shouldSelectById() {
//        DemoEntity saved = saveDemoEntity("Bob", 25);
//        Long id = saved.getId();
//
//        DemoEntity found = demoMapper.selectById(id);
//
//        assertThat(found).isNotNull();
//        assertThat(found.getId()).isEqualTo(id);
//        assertThat(found.getName()).isEqualTo("Bob");
//        assertThat(found.getAge()).isEqualTo(25);
//        assertThat(found.getCreateTime()).isNotNull();
//        assertThat(found.getUpdateTime()).isNotNull();
//        assertThat(found.getDeleted()).isEqualTo(0);
//        assertThat(found.getVersion()).isEqualTo(1);
//    }
//
//    @Test
//    void shouldUpdateWithOptimisticLock() {
//        DemoEntity saved = saveDemoEntity("Carol", 28);
//        Long id = saved.getId();
//        Integer originalVersion = saved.getVersion();
//
//        saved.setAge(29);
//        int rows = demoMapper.updateById(saved);
//
//        assertThat(rows).isEqualTo(1);
//
//        DemoEntity updated = demoMapper.selectById(id);
//        assertThat(updated.getVersion()).isEqualTo(originalVersion + 1);
//        assertThat(updated.getAge()).isEqualTo(29);
//        assertThat(updated.getUpdateTime()).isAfterOrEqualTo(updated.getCreateTime());
//    }
//
//    @Test
//    void shouldLogicDelete() {
//        DemoEntity saved = saveDemoEntity("Dave", 35);
//        Long id = saved.getId();
//
//        int rows = demoMapper.deleteById(id);
//        assertThat(rows).isEqualTo(1);
//
//        DemoEntity found = demoMapper.selectById(id);
//        assertThat(found).isNull();
//
//        Integer deleted = jdbcTemplate.queryForObject("SELECT deleted FROM demo_user WHERE id = ?", Integer.class, id);
//        assertThat(deleted).isEqualTo(1);
//    }
//
//    @Test
//    void shouldPageQuery() {
//        for (int i = 1; i <= 20; i++) {
//            DemoEntity entity = new DemoEntity();
//            entity.setName("User" + i);
//            entity.setAge(20 + i);
//            demoMapper.insert(entity);
//        }
//
//        Page<DemoEntity> page = new Page<>(1, 10);
//        Page<DemoEntity> result = demoMapper.selectPage(page, new QueryWrapper<>());
//
//        assertThat(result.getTotal()).isEqualTo(20);
//        assertThat(result.getCurrent()).isEqualTo(1);
//        assertThat(result.getSize()).isEqualTo(10);
//        assertThat(result.getRecords()).hasSize(10);
//    }
//}

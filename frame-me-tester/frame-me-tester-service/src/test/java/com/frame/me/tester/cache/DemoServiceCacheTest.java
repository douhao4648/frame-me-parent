package com.frame.me.tester.cache;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.frame.me.tester.api.dto.DemoDTO;
import com.frame.me.tester.api.vo.DemoVO;
import com.frame.me.tester.service.IDemoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DemoService 缓存行为测试.
 *
 * <p>验证 {@code @Cached} 与 {@code @CacheInvalidate} 注解是否生效，使用 H2 内存数据库
 * 与 Caffeine 本地缓存，不依赖 Redis。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DemoServiceCacheTest {

    @Autowired
    private IDemoService demoService;

    @Autowired
    private CacheManager cacheManager;

    private Cache<Long, DemoVO> detailCache;

    @BeforeEach
    void setUp() {
        QuickConfig config = QuickConfig.newBuilder("demo:detail:")
                .cacheType(CacheType.LOCAL)
                .localLimit(100)
                .localExpire(Duration.ofSeconds(600))
                .expire(Duration.ofSeconds(1800))
                .cacheNullValue(true)
                .penetrationProtect(true)
                .build();
        detailCache = cacheManager.getOrCreateCache(config);
    }

    @AfterEach
    void tearDown() {
        if (detailCache != null) {
            detailCache.close();
        }
    }

    @Test
    void cacheHitAfterFirstCall() {
        Long id = createDemo("cache-hit-test");

        // 第一次调用，应未命中缓存，从数据库加载
        DemoVO first = demoService.getById(id);
        assertThat(first).isNotNull();
        assertThat(detailCache.get(id)).isNotNull();

        // 第二次调用，应命中本地缓存
        DemoVO second = demoService.getById(id);
        assertThat(second).isNotNull();
        assertThat(second.getName()).isEqualTo(first.getName());
    }

    @Test
    void cacheInvalidateOnDelete() {
        Long id = createDemo("cache-invalidate-test");

        demoService.getById(id);
        assertThat(detailCache.get(id)).isNotNull();

        // 删除后缓存应失效
        demoService.delete(id);
        assertThat(detailCache.get(id)).isNull();
    }

    private Long createDemo(String name) {
        DemoDTO dto = new DemoDTO();
        dto.setName(name);
        dto.setAge(20);
        dto.setVersion(1);
        return demoService.create(dto);
    }

}

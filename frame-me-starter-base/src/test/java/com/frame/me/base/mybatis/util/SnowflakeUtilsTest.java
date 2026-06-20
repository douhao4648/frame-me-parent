package com.frame.me.base.mybatis.util;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SnowflakeUtils 单元测试.
 */
class SnowflakeUtilsTest {

    @BeforeEach
    void setUp() throws Exception {
        setIdentifierGenerator(DefaultIdentifierGenerator.getInstance());
    }

    /**
     * 通过反射设置静态 identifierGenerator 字段.
     */
    private void setIdentifierGenerator(IdentifierGenerator generator) throws Exception {
        Field field = SnowflakeUtils.class.getDeclaredField("identifierGenerator");
        field.setAccessible(true);
        field.set(null, generator);
    }

    @Test
    void shouldGeneratePositiveLongId() {
        long id = SnowflakeUtils.nextId();

        assertTrue(id > 0, "生成的雪花 ID 应该大于 0");
    }

    @Test
    void shouldGenerateStringId() {
        String idStr = SnowflakeUtils.nextIdStr();

        assertNotNull(idStr);
        assertTrue(idStr.matches("\\d+"), "字符串 ID 应该只包含数字");
        assertTrue(Long.parseLong(idStr) > 0, "字符串 ID 解析后应该大于 0");
    }

    @Test
    void shouldGenerateUniqueIds() {
        int count = 1000;
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < count; i++) {
            ids.add(SnowflakeUtils.nextId());
        }

        assertEquals(count, ids.size(), "生成的 %d 个 ID 应该互不相同".formatted(count));
    }
}

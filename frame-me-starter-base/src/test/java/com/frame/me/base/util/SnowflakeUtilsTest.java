package com.frame.me.base.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SnowflakeUtils 单元测试.
 */
class SnowflakeUtilsTest {

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

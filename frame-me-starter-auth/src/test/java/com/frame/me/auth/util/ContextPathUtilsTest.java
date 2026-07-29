package com.frame.me.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ContextPathUtils} 单元测试.
 *
 * @author frame-me
 */
class ContextPathUtilsTest {

    @Test
    void testStripPrefixedPattern() {
        assertEquals("/api/public/**", ContextPathUtils.stripContextPath("/app/api/public/**", "/app"));
    }

    @Test
    void testExactContextPathBecomesRoot() {
        assertEquals("/", ContextPathUtils.stripContextPath("/app", "/app"));
    }

    @Test
    void testSimilarPrefixNotStripped() {
        // /app2 与 /app 仅前缀相同，不是 context-path 前缀，不得误剥离
        assertEquals("/app2/api/**", ContextPathUtils.stripContextPath("/app2/api/**", "/app"));
    }

    @Test
    void testNoContextPathReturnsAsIs() {
        assertEquals("/app/api/**", ContextPathUtils.stripContextPath("/app/api/**", null));
        assertEquals("/app/api/**", ContextPathUtils.stripContextPath("/app/api/**", ""));
    }

    @Test
    void testPatternWithoutPrefixReturnsAsIs() {
        assertEquals("/api/**", ContextPathUtils.stripContextPath("/api/**", "/app"));
    }
}

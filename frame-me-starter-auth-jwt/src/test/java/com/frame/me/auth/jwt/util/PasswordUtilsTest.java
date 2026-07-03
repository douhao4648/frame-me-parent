package com.frame.me.auth.jwt.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PasswordUtils} 单元测试.
 *
 * @author frame-me
 */
class PasswordUtilsTest {

    @Test
    void testEncodeAndMatches() {
        String raw = "123456";
        String encoded = PasswordUtils.encode(raw);

        assertNotEquals(raw, encoded);
        assertTrue(PasswordUtils.matches(raw, encoded));
        assertFalse(PasswordUtils.matches("wrong", encoded));
    }

    @Test
    void testDifferentRawPasswordsDoNotMatch() {
        String encoded = PasswordUtils.encode("password");
        assertFalse(PasswordUtils.matches("password2", encoded));
    }
}

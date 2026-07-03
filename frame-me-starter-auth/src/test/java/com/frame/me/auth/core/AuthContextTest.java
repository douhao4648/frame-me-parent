package com.frame.me.auth.core;

import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AuthContext} 单元测试.
 *
 * @author frame-me
 */
class AuthContextTest {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void testSetAndGetUser() {
        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        AuthContext.setUser(user);

        assertEquals(1L, AuthContext.getUserId());
        assertEquals("admin", AuthContext.getAccount());
        assertEquals(user, AuthContext.getUser());
    }

    @Test
    void testClear() {
        User user = new User();
        user.setId(1L);
        AuthContext.setUser(user);
        AuthContext.clear();

        assertNull(AuthContext.getUser());
        assertNull(AuthContext.getUserId());
        assertNull(AuthContext.getAccount());
    }
}

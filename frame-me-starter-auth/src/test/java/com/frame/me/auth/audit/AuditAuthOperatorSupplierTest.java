package com.frame.me.auth.audit;

import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AuditAuthOperatorSupplier} 单元测试.
 *
 * @author frame-me
 */
class AuditAuthOperatorSupplierTest {

    private final AuditAuthOperatorSupplier supplier = new AuditAuthOperatorSupplier();

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void testGetOperatorIdWithUser() {
        User user = new User();
        user.setId(42L);
        AuthContext.setUser(user);

        assertEquals("42", supplier.getOperatorId());
    }

    @Test
    void testGetOperatorIdWithoutUser() {
        assertEquals("anonymous", supplier.getOperatorId());
    }
}

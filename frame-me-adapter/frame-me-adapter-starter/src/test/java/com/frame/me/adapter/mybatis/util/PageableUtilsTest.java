package com.frame.me.adapter.mybatis.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.adapter.api.query.PageParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageableUtils} 单元测试.
 *
 * @author frame-me
 */
class PageableUtilsTest {

    @Test
    void testValidColumnAdded() {
        PageParam param = new PageParam();
        param.setOrders(List.of(order("create_time", false), order("t.id", true)));

        Page<Object> page = PageableUtils.toPage(param);

        assertEquals(2, page.orders().size());
        assertEquals("create_time", page.orders().get(0).getColumn());
        assertFalse(page.orders().get(0).isAsc());
        assertEquals("t.id", page.orders().get(1).getColumn());
    }

    @Test
    void testInjectionColumnDropped() {
        PageParam param = new PageParam();
        param.setOrders(List.of(
                order("id, (select password from user limit 1)", true),
                order("1;drop table user", true),
                order("id--", true)));

        Page<Object> page = PageableUtils.toPage(param);

        assertTrue(page.orders().isEmpty());
    }

    @Test
    void testMixedValidAndInvalidColumns() {
        PageParam param = new PageParam();
        param.setOrders(List.of(order("name", true), order("name desc", true)));

        Page<Object> page = PageableUtils.toPage(param);

        assertEquals(1, page.orders().size());
        assertEquals("name", page.orders().get(0).getColumn());
    }

    @Test
    void testNullAndBlankColumnDropped() {
        PageParam param = new PageParam();
        param.setOrders(List.of(order(null, true), order("  ", true)));

        Page<Object> page = PageableUtils.toPage(param);

        assertTrue(page.orders().isEmpty());
    }

    private PageParam.OrderItem order(String column, boolean asc) {
        PageParam.OrderItem item = new PageParam.OrderItem();
        item.setColumn(column);
        item.setAsc(asc);
        return item;
    }
}

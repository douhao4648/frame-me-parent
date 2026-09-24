package com.frame.me.mybatis.flex.util;

import com.frame.me.api.query.PageQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageUtils} 分页参数转换测试.
 *
 * <p>重点锁定 long → int 饱和转换：超出 int 范围的页码不得回绕成负数或错误正页码.</p>
 *
 * @author frame-me
 */
class PageUtilsTest {

    /**
     * 页码转换：null/非正数回退 1；正常值原样通过；超出 int 范围饱和到 Integer.MAX_VALUE.
     */
    @Test
    void pageNumberSaturatesInsteadOfOverflowing() {
        assertThat(PageUtils.pageNumber(new PageQuery())).isEqualTo(1);

        PageQuery query = new PageQuery();
        query.setCurrent(null);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(1);

        query.setCurrent(0L);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(1);
        query.setCurrent(-5L);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(1);

        query.setCurrent(5L);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(5);

        query.setCurrent((long) Integer.MAX_VALUE);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(Integer.MAX_VALUE);

        // 直接强转会回绕成 Integer.MIN_VALUE（负数页码）
        query.setCurrent((long) Integer.MAX_VALUE + 1);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(Integer.MAX_VALUE);

        // 直接强转会回绕成 5（静默错页）
        query.setCurrent((1L << 32) + 5);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(Integer.MAX_VALUE);

        query.setCurrent(Long.MAX_VALUE);
        assertThat(PageUtils.pageNumber(query)).isEqualTo(Integer.MAX_VALUE);
    }

    /**
     * 每页条数：null/非正数回退 10；超过上限钳到 1000.
     */
    @Test
    void pageSizeDefaultsAndClamps() {
        PageQuery query = new PageQuery();
        query.setSize(null);
        assertThat(PageUtils.pageSize(query)).isEqualTo(10);

        query.setSize(0L);
        assertThat(PageUtils.pageSize(query)).isEqualTo(10);

        query.setSize(50L);
        assertThat(PageUtils.pageSize(query)).isEqualTo(50);

        query.setSize(5000L);
        assertThat(PageUtils.pageSize(query)).isEqualTo(1000);
    }
}

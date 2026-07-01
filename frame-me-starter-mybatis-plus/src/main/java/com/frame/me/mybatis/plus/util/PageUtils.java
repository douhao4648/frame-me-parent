package com.frame.me.mybatis.plus.util;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.api.query.PageQuery;
import com.frame.me.api.result.PageData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类.
 *
 * <p>用于在 API 层 {@link PageQuery} / {@link PageData} 与 MyBatis-Plus {@link Page} 之间转换。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageUtils {

    /**
     * 将 {@link PageQuery} 转换为 MyBatis-Plus {@link Page}.
     *
     * @param query 分页查询参数
     * @param <T>   业务数据类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toPage(PageQuery query) {
        return toPage(query, null);
    }

    /**
     * 将 {@link PageQuery} 转换为 MyBatis-Plus {@link Page}，并在无请求排序时使用默认排序.
     *
     * @param query          分页查询参数
     * @param defaultOrderBy 默认排序字符串，格式 {@code 字段名[:方向]}
     * @param <T>            业务数据类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toPage(PageQuery query, String defaultOrderBy) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        Page<T> page = new Page<>(current, size);

        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            query.getOrderBy().forEach(item -> addOrder(page, item));
        } else if (defaultOrderBy != null && !defaultOrderBy.isBlank()) {
            addOrder(page, defaultOrderBy);
        }

        return page;
    }

    /**
     * 将 MyBatis-Plus {@link Page} 转换为 {@link PageData}.
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  业务数据类型
     * @return 分页结果
     */
    public static <T> PageData<T> toPageData(Page<T> page) {
        PageData<T> result = new PageData<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords());
        return result;
    }

    /**
     * 将 MyBatis-Plus {@link Page} 转换为 {@link PageData}，并对记录做映射转换.
     *
     * @param page   MyBatis-Plus 分页对象
     * @param mapper 记录映射函数
     * @param <T>    源数据类型
     * @param <R>    目标数据类型
     * @return 分页结果
     */
    public static <T, R> PageData<R> toPageData(Page<T> page, Function<T, R> mapper) {
        PageData<R> result = new PageData<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords().stream().map(mapper).collect(Collectors.toList()));
        return result;
    }

    private static <T> void addOrder(Page<T> page, String item) {
        if (item == null || item.isBlank()) {
            return;
        }
        String[] parts = item.split(":");
        String column = parts[0].trim();
        boolean asc = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
        page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
    }
}

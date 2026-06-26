package com.frame.me.adapter.mybatis.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.adapter.api.query.PageParam;
import com.frame.me.adapter.api.result.PageResult;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 老规范分页工具类.
 *
 * <p>用于在适配层 {@link PageParam} / {@link PageResult} 与 MyBatis-Plus {@link Page} 之间转换，
 * 供需要兼容老接口规范的项目使用。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageableUtils {

    /**
     * 将 {@link PageParam} 转换为 MyBatis-Plus {@link Page}.
     *
     * @param param 分页请求参数
     * @param <T>   业务数据类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toPage(PageParam param) {
        long current = param.getPageNum() == null || param.getPageNum() < 1 ? 1 : param.getPageNum();
        long size = param.getPageSize() == null || param.getPageSize() < 1 ? 10 : param.getPageSize();
        Page<T> page = new Page<>(current, size);

        if (param.getSearchCount() != null) {
            page.setSearchCount(param.getSearchCount());
        }
        if (param.getOrders() != null) {
            param.getOrders().forEach(order -> addOrder(page, order));
        }

        return page;
    }

    /**
     * 将 MyBatis-Plus {@link Page} 转换为 {@link PageResult}.
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  业务数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> toPageResult(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(page.getRecords());
        return result;
    }

    /**
     * 将 MyBatis-Plus {@link Page} 转换为 {@link PageResult}，并对记录做映射转换.
     *
     * @param page   MyBatis-Plus 分页对象
     * @param mapper 记录映射函数
     * @param <T>    源数据类型
     * @param <R>    目标数据类型
     * @return 分页结果
     */
    public static <T, R> PageResult<R> toPageResult(Page<T> page, Function<T, R> mapper) {
        PageResult<R> result = new PageResult<>();
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(page.getRecords().stream().map(mapper).collect(Collectors.toList()));
        return result;
    }

    private static <T> void addOrder(Page<T> page, PageParam.OrderItem order) {
        if (order == null || order.getColumn() == null || order.getColumn().isBlank()) {
            return;
        }
        String column = order.getColumn().trim();
        page.addOrder(order.isAsc()
                ? com.baomidou.mybatisplus.core.metadata.OrderItem.asc(column)
                : com.baomidou.mybatisplus.core.metadata.OrderItem.desc(column));
    }
}

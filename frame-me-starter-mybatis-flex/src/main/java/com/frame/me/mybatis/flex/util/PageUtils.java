package com.frame.me.mybatis.flex.util;

import com.frame.me.api.query.PageQuery;
import com.frame.me.api.result.PageData;
import com.mybatisflex.core.paginate.Page;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类.
 *
 * <p>用于在 API 层 {@link PageQuery} / {@link PageData} 与 MyBatis-Flex {@link Page} 之间转换。
 * MyBatis-Flex 分页调用为 {@code mapper.paginate(pageNumber, pageSize, queryWrapper)}。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageUtils {

    /**
     * 从 {@link PageQuery} 中提取当前页码（1-based）.
     *
     * @param query 分页查询参数
     * @return 当前页码
     */
    public static int getPageNumber(PageQuery query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        return (int) current;
    }

    /**
     * 从 {@link PageQuery} 中提取每页条数.
     *
     * @param query 分页查询参数
     * @return 每页条数
     */
    public static int getPageSize(PageQuery query) {
        long size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        return (int) size;
    }

    /**
     * 将 MyBatis-Flex {@link Page} 转换为 {@link PageData}.
     *
     * @param page MyBatis-Flex 分页对象
     * @param <T>  业务数据类型
     * @return 分页结果
     */
    public static <T> PageData<T> toPageData(Page<T> page) {
        PageData<T> result = new PageData<>();
        result.setCurrent((long) page.getPageNumber());
        result.setSize((long) page.getPageSize());
        result.setTotal(page.getTotalRow());
        result.setPages((long) page.getTotalPage());
        result.setRecords(page.getRecords());
        return result;
    }

    /**
     * 将 MyBatis-Flex {@link Page} 转换为 {@link PageData}，并对记录做映射转换.
     *
     * @param page   MyBatis-Flex 分页对象
     * @param mapper 记录映射函数
     * @param <T>    源数据类型
     * @param <R>    目标数据类型
     * @return 分页结果
     */
    public static <T, R> PageData<R> toPageData(Page<T> page, Function<T, R> mapper) {
        PageData<R> result = new PageData<>();
        result.setCurrent((long) page.getPageNumber());
        result.setSize((long) page.getPageSize());
        result.setTotal(page.getTotalRow());
        result.setPages((long) page.getTotalPage());
        result.setRecords(page.getRecords().stream().map(mapper).collect(Collectors.toList()));
        return result;
    }
}

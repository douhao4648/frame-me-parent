package com.frame.me.mybatis.plus.util;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.api.query.PageQuery;
import com.frame.me.api.result.PageData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
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
        String[] parts = item.split(" ");
        String column = parts[0].trim();
        if (!SAFE_COLUMN.matcher(column).matches()) {
            return;
        }
        boolean asc = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
        page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
    }

    /**
     * 合法排序字段名的白名单模式：仅允许字母、数字、下划线以及点（table.column）.
     */
    private static final Pattern SAFE_COLUMN = Pattern.compile("^[A-Za-z0-9_.]+$");

    /**
     * 将单个排序段解析为安全的 ORDER BY SQL 片段.
     *
     * <p>支持 {@code 字段名 asc}、{@code 字段名 desc}，方向省略时默认升序。
     * 字段名必须通过 {@link #SAFE_COLUMN} 白名单校验。不合法的段返回 {@code null}。</p>
     *
     * @param segment 排序段，如 {@code "create_time desc"}
     * @return 安全排序 SQL 片段，或 {@code null}
     */
    private static String parseSegment(String segment) {
        if (segment == null) {
            return null;
        }
        String normalized = segment.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split(" ", 2);
        String column = parts[0].trim();
        if (!SAFE_COLUMN.matcher(column).matches()) {
            return null;
        }
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return column + (desc ? " desc" : " asc");
    }

    /**
     * 将可能包含多个排序段的字符串按逗号拆分并解析为安全 SQL 片段.
     *
     * @param raw 原始排序字符串，如 {@code "create_time desc,id asc"}
     * @return 安全排序 SQL 片段列表
     */
    private static List<String> parseOrderBy(String raw) {
        List<String> safe = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return safe;
        }
        for (String segment : raw.split(",")) {
            String item = parseSegment(segment);
            if (item != null) {
                safe.add(item);
            }
        }
        return safe;
    }

    /**
     * 将 {@link PageQuery} 的排序字段解析为逗号拼接的 ORDER BY SQL 字符串.
     *
     * <p>{@link PageQuery#getOrderBy()} 格式为 {@code 字段名[ asc|desc]}，方向省略时默认升序。
     * 排序字段来自前端，为防止 SQL 注入，字段名会按白名单 {@link #SAFE_COLUMN} 校验，
     * 非法项会被丢弃。若前端无任何合法排序项，则回退到 {@code defaultOrderBy}。</p>
     *
     * @param query          分页查询参数
     * @param defaultOrderBy 默认排序 SQL 片段，当 {@code orderBy} 为空或全部非法时使用
     * @return ORDER BY SQL 字符串，无合法排序项时返回空串
     */
    public static String toOrderByStr(PageQuery query, String defaultOrderBy) {
        List<String> safe;
        if (query.getOrderBy() == null || query.getOrderBy().isEmpty()) {
            safe = parseOrderBy(defaultOrderBy);
        } else {
            safe = new ArrayList<>(query.getOrderBy().size());
            for (String raw : query.getOrderBy()) {
                safe.addAll(parseOrderBy(raw));
            }
        }
        if (safe.isEmpty()) {
            safe = parseOrderBy(defaultOrderBy);
        }
        return String.join(", ", safe);
    }

    /**
     * 将 {@code defaultOrderBy} 解析为逗号拼接的 ORDER BY SQL 字符串.
     *
     * @param defaultOrderBy 默认排序 SQL 片段
     * @return ORDER BY SQL 字符串，无合法排序项时返回空串
     */
    public static String toOrderByStr(String defaultOrderBy) {
        return String.join(", ", parseOrderBy(defaultOrderBy));
    }
}

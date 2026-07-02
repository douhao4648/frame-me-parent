package com.frame.me.mybatis.flex.util;

import com.frame.me.api.query.PageQuery;
import com.frame.me.api.result.PageData;
import com.mybatisflex.core.paginate.Page;
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
 * <p>用于在 API 层 {@link PageQuery} / {@link PageData} 与 MyBatis-Flex {@link Page} 之间转换。
 * MyBatis-Flex 分页调用为 {@code mapper.paginate(pageNumber, pageSize, queryWrapper)}。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageUtils {

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
     * 从 {@link PageQuery} 中提取当前页码（1-based）.
     *
     * @param query 分页查询参数
     * @return 当前页码
     */
    public static int pageNumber(PageQuery query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        return (int) current;
    }

    /**
     * 从 {@link PageQuery} 中提取每页条数.
     *
     * @param query 分页查询参数
     * @return 每页条数
     */
    public static int pageSize(PageQuery query) {
        long size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        return (int) size;
    }

    /**
     * 将 {@link PageQuery} 转换为 MyBatis-Flex {@link Page}.
     *
     * @param query 分页查询参数
     * @param <T>   业务数据类型
     * @return MyBatis-Flex 分页对象
     */
    public static <T> Page<T> toPage(PageQuery query) {
        return Page.of(pageNumber(query), pageSize(query));
    }


    /**
     * 将 {@link PageQuery} 的排序字段解析为 ORDER BY SQL 片段.
     *
     * <p>{@link PageQuery#getOrderBy()} 格式为 {@code 字段名[ asc|desc]}，方向省略时默认升序，
     * 多个字段间用逗号分隔。排序字段来自前端，为防止 SQL 注入，字段名会按白名单
     * {@link #SAFE_COLUMN} 校验，方向只接受 {@code asc}/{@code desc}（不区分大小写），
     * 非法项会被丢弃。{@code defaultOrderBy} 也按同样规则解析，支持多段。
     * 若前端无任何合法排序项，则返回 {@code defaultOrderBy} 的解析结果。
     * 返回的字符串可直接用于 {@code QueryWrapper#orderBy(String...)}。</p>
     *
     * @param query          分页查询参数
     * @param defaultOrderBy 默认排序 SQL 片段，当 {@code orderBy} 为空或全部非法时返回
     * @return ORDER BY SQL 片段
     */
    public static String[] toOrderBy(PageQuery query, String defaultOrderBy) {
        List<String> orderByList = query.getOrderBy();
        List<String> safe;
        if (orderByList == null || orderByList.isEmpty()) {
            safe = parseOrderBy(defaultOrderBy);
        } else {
            safe = new ArrayList<>(orderByList.size());
            for (String raw : orderByList) {
                safe.addAll(parseOrderBy(raw));
            }
        }
        if (safe.isEmpty()) {
            safe = parseOrderBy(defaultOrderBy);
        }
        return safe.toArray(String[]::new);
    }

    /**
     * 将 {@code defaultOrderBy} 解析为 ORDER BY SQL 片段.
     *
     * <p>不含前端排序参数，仅按白名单 {@link #SAFE_COLUMN} 解析默认排序，支持多段（逗号分隔）。</p>
     *
     * @param defaultOrderBy 默认排序 SQL 片段
     * @return ORDER BY SQL 片段
     */
    public static String[] toOrderBy(String defaultOrderBy) {
        return parseOrderBy(defaultOrderBy).toArray(String[]::new);
    }

    /**
     * 同 {@link #toOrderBy(PageQuery, String)}，但返回逗号拼接的单个 ORDER BY 字符串.
     *
     * @param query          分页查询参数
     * @param defaultOrderBy 默认排序 SQL 片段，当 {@code orderBy} 为空或全部非法时返回
     * @return ORDER BY SQL 字符串，无合法排序项时返回空串
     */
    public static String toOrderByStr(PageQuery query, String defaultOrderBy) {
        return String.join(", ", toOrderBy(query, defaultOrderBy));
    }

    /**
     * 同 {@link #toOrderBy(String)}，但返回逗号拼接的单个 ORDER BY 字符串.
     *
     * @param defaultOrderBy 默认排序 SQL 片段
     * @return ORDER BY SQL 字符串，无合法排序项时返回空串
     */
    public static String toOrderByStr(String defaultOrderBy) {
        return String.join(", ", toOrderBy(defaultOrderBy));
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

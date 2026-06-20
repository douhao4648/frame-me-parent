package com.frame.me.base.mybatis.query;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * 基础分页查询参数.
 *
 * <p>业务查询类可继承此类，复用 {@code current} / {@code size} 分页参数，
 * 并通过 {@link #toPage()} 快速构造 MyBatis-Plus 的 {@link Page} 对象。
 */
@Data
public class PageQuery {

    /**
     * 当前页码，默认 1.
     */
    private Long current = 1L;

    /**
     * 每页条数，默认 10.
     */
    private Long size = 10L;

    /**
     * 排序字段列表.
     *
     * <p>前端通过同名参数多次传递，如：
     * <pre>?orderBy=create_time:desc&orderBy=age:asc</pre>
     *
     * <p>每个元素格式为 {@code 字段名[:方向]}，方向可选 {@code asc} 或 {@code desc}，
     * 省略时默认升序。
     */
    private List<String> orderBy;

    /**
     * 转换为 MyBatis-Plus 分页对象.
     *
     * <p>子类可覆盖此方法，添加默认排序或自定义分页配置。
     *
     * @param <T> 业务数据类型
     * @return 分页对象
     */
    public <T> Page<T> toPage() {
        long currentValue = current == null || current < 1 ? 1 : current;
        long sizeValue = size == null || size < 1 ? 10 : size;
        Page<T> page = new Page<>(currentValue, sizeValue);

        if (CollUtil.isNotEmpty(orderBy)) {
            for (String item : orderBy) {
                if (StrUtil.isBlank(item)) {
                    continue;
                }
                String[] parts = item.split(":");
                String column = parts[0].trim();
                boolean asc = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
                page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
            }
        }

        return page;
    }
}

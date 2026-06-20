package com.frame.me.tester.query;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frame.me.base.mybatis.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 演示数据查询参数.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemoQuery extends PageQuery {

    /**
     * 姓名，支持模糊查询.
     */
    private String name;

    /**
     * 年龄，精确查询.
     */
    private Integer age;

    /**
     * 默认按创建时间降序排序；如果请求传了 {@code orderBy}，则以请求为准.
     *
     * @param <T> 业务数据类型
     * @return 分页对象
     */
    @Override
    public <T> Page<T> toPage() {
        Page<T> page = super.toPage();
        if (CollUtil.isEmpty(getOrderBy())) {
            page.addOrder(OrderItem.desc("create_time"));
        }
        return page;
    }
}

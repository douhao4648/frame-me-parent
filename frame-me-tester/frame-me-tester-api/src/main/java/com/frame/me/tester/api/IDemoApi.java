package com.frame.me.tester.api;

import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageResult;
import com.frame.me.tester.api.dto.DemoDTO;
import com.frame.me.tester.api.query.DemoComplexQuery;
import com.frame.me.tester.api.query.DemoQuery;
import com.frame.me.tester.api.vo.DemoComplexVO;
import com.frame.me.tester.api.vo.DemoVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * 演示数据 API 契约.
 */
@HttpExchange("/demo")
public interface IDemoApi {

    /**
     * 查询演示数据列表.
     *
     * @return 演示数据列表
     */
    @GetExchange("/list")
    IResult<List<DemoVO>> list();

    /**
     * 分页查询演示数据.
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetExchange("/page")
    IResult<PageResult<DemoVO>> page(DemoQuery query);

    /**
     * 复杂查询演示数据（手写 SQL）.
     *
     * @param query 复杂查询参数
     * @return 复杂查询结果列表
     */
    @GetExchange("/complex-list")
    IResult<List<DemoComplexVO>> complexList(DemoComplexQuery query);

    /**
     * 根据 ID 查询演示数据.
     *
     * @param id 数据 ID
     * @return 演示数据
     */
    @GetExchange("/{id}")
    IResult<DemoVO> getById(@PathVariable Long id);

    /**
     * 插入演示数据.
     *
     * @param dto 演示数据 DTO
     * @return 新增数据 ID
     */
    @PostExchange
    IResult<Long> create(@RequestBody DemoDTO dto);

    /**
     * 根据 ID 更新演示数据.
     *
     * @param id  数据 ID
     * @param dto 演示数据 DTO，需包含 version 用于乐观锁控制
     * @return 是否更新成功
     */
    @PutExchange("/{id}")
    IResult<Boolean> update(@PathVariable Long id, @RequestBody DemoDTO dto);

    /**
     * 根据 ID 删除演示数据（逻辑删除）.
     *
     * @param id 数据 ID
     * @return 是否删除成功
     */
    @DeleteExchange("/{id}")
    IResult<Boolean> delete(@PathVariable Long id);
}

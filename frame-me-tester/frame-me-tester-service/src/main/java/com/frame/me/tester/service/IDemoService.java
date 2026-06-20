package com.frame.me.tester.service;

import com.frame.me.api.result.PageResult;
import com.frame.me.tester.api.dto.DemoDTO;
import com.frame.me.tester.api.query.DemoComplexQuery;
import com.frame.me.tester.api.query.DemoQuery;
import com.frame.me.tester.api.vo.DemoComplexVO;
import com.frame.me.tester.api.vo.DemoVO;

import java.util.List;

/**
 * Demo 业务服务接口.
 */
public interface IDemoService {

    /**
     * 查询全部演示数据.
     *
     * @return 演示数据列表
     */
    List<DemoVO> list();

    /**
     * 分页查询演示数据.
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<DemoVO> page(DemoQuery query);

    /**
     * 复杂查询演示数据（手写 SQL）.
     *
     * @param query 复杂查询参数
     * @return 复杂查询结果列表
     */
    List<DemoComplexVO> complexList(DemoComplexQuery query);

    /**
     * 根据 ID 查询演示数据.
     *
     * @param id 数据 ID
     * @return 演示数据
     */
    DemoVO getById(Long id);

    /**
     * 新增演示数据.
     *
     * @param dto 演示数据 DTO
     * @return 新增数据 ID
     */
    Long create(DemoDTO dto);

    /**
     * 根据 ID 更新演示数据.
     *
     * @param id  数据 ID
     * @param dto 演示数据 DTO
     * @return 是否更新成功
     */
    Boolean update(Long id, DemoDTO dto);

    /**
     * 根据 ID 删除演示数据.
     *
     * @param id 数据 ID
     * @return 是否删除成功
     */
    Boolean delete(Long id);
}

package com.frame.me.tester.service;

import com.frame.me.api.result.PageData;
import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.api.query.FlexDemoQuery;
import com.frame.me.tester.api.vo.FlexDemoVO;

import java.util.List;

/**
 * Flex 演示业务接口.
 */
public interface IFlexDemoService {

    List<FlexDemoVO> list();

    PageData<FlexDemoVO> page(FlexDemoQuery query);

    FlexDemoVO getById(Long id);

    /**
     * 向主数据源（master）新增.
     *
     * @param dto 请求数据
     * @return 新增记录主键
     */
    Long create(FlexDemoDTO dto);

    /**
     * 使用 {@code @DS("second")} 向 second 数据源新增.
     *
     * @param dto 请求数据
     * @return 新增记录主键
     */
    Long createInSecond(FlexDemoDTO dto);

    Boolean update(Long id, FlexDemoDTO dto);

    Boolean delete(Long id);

    /**
     * 统计 master 数据源条数.
     *
     * @return 条数
     */
    long count();

    /**
     * 使用 {@code @DS("second")} 统计 second 数据源条数.
     *
     * @return 条数
     */
    long countInSecond();

    /**
     * 通过 mapper.xml 自定义 SQL 查询.
     *
     * @param query 查询参数
     * @return 数据列表
     */
    List<FlexDemoVO> listByXml(FlexDemoQuery query);
}

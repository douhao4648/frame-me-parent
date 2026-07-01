package com.frame.me.tester.mapper;

import com.frame.me.tester.api.query.FlexDemoQuery;
import com.frame.me.tester.api.vo.FlexDemoVO;
import com.frame.me.tester.entity.FlexDemoEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Flex 演示数据 Mapper，继承 MyBatis-Flex {@link BaseMapper}.
 */
@Mapper
public interface FlexDemoMapper extends BaseMapper<FlexDemoEntity> {

    /**
     * 通过 mapper.xml 自定义 SQL 查询列表.
     *
     * @param query 查询参数
     * @return 数据列表
     */
    List<FlexDemoVO> listByXml(@Param("query") FlexDemoQuery query);
}

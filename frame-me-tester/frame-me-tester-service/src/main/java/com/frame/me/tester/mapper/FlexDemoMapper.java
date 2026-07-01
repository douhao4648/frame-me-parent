package com.frame.me.tester.mapper;

import com.frame.me.tester.entity.FlexDemoEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Flex 演示数据 Mapper，继承 MyBatis-Flex {@link BaseMapper}.
 */
@Mapper
public interface FlexDemoMapper extends BaseMapper<FlexDemoEntity> {
}

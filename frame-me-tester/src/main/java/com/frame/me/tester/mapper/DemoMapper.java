package com.frame.me.tester.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frame.me.tester.entity.DemoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示 Mapper 接口.
 */
@Mapper
public interface DemoMapper extends BaseMapper<DemoEntity> {
}

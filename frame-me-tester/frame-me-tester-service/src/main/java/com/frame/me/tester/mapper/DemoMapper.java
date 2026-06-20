package com.frame.me.tester.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frame.me.tester.api.vo.DemoComplexVO;
import com.frame.me.tester.entity.DemoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 演示 Mapper 接口.
 */
@Mapper
public interface DemoMapper extends BaseMapper<DemoEntity> {

    /**
     * 复杂查询：根据年龄区间和创建时间范围查询，并返回年龄分组.
     * <p>对应的 SQL 定义在 classpath:mapper/DemoMapper.xml 中。
     *
     * @param minAge    最小年龄
     * @param maxAge    最大年龄
     * @param startTime 创建时间起始
     * @param endTime   创建时间截止
     * @return 复杂查询结果
     */
    List<DemoComplexVO> selectComplexList(@Param("minAge") Integer minAge,
                                          @Param("maxAge") Integer maxAge,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);
}

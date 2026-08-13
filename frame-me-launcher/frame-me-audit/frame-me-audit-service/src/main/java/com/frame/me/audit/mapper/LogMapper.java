package com.frame.me.audit.mapper;

import com.frame.me.audit.entity.LogEntity;
import com.frame.me.audit.api.vo.LogVO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志 Mapper.
 *
 * @author frame-me
 */
@Mapper
public interface LogMapper extends BaseMapper<LogEntity> {

    /**
     * 通过 mapper.xml 自定义 SQL 按 ID 查询单条（详情查询统一走 XML）.
     *
     * @param id 日志 ID
     * @return 审计日志，不存在或未命中返回 null
     */
    LogVO getById(@Param("id") Long id);
}

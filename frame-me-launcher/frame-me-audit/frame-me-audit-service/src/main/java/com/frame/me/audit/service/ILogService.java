package com.frame.me.audit.service;

import com.frame.me.api.result.PageData;
import com.frame.me.audit.api.query.LogQuery;
import com.frame.me.audit.api.vo.LogVO;

import java.util.List;

/**
 * 审计日志查询业务接口.
 *
 * @author frame-me
 */
public interface ILogService {

    /**
     * 按条件搜索审计日志列表（不分页，按事件发生时间倒序）.
     *
     * @param query 查询参数
     * @return 审计日志列表
     */
    List<LogVO> list(LogQuery query);

    /**
     * 按条件搜索审计日志并分页返回.
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageData<LogVO> page(LogQuery query);

    /**
     * 根据主键 ID 查询单条审计日志（走 mapper.xml 自定义 SQL）.
     *
     * @param id 日志 ID
     * @return 审计日志详情
     */
    LogVO getById(Long id);
}

package com.frame.me.audit.service.impl;

import cn.hutool.core.util.StrUtil;
import com.frame.me.api.result.PageData;
import com.frame.me.audit.api.query.LogQuery;
import com.frame.me.audit.api.vo.LogVO;
import com.frame.me.audit.entity.LogEntity;
import com.frame.me.audit.entity.table.LogEntityTableDef;
import com.frame.me.audit.mapper.LogMapper;
import com.frame.me.audit.service.ILogService;
import com.frame.me.audit.service.convert.LogConvert;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.mybatis.flex.util.PageUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志查询业务实现.
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements ILogService {

    /**
     * 默认排序：事件发生时间倒序（最新在前）.
     */
    private static final String DEFAULT_ORDER_BY = "timestamp desc";

    private final LogMapper auditLogMapper;
    private final LogConvert auditLogConvert;

    @Override
    public List<LogVO> list(LogQuery query) {
        return auditLogConvert.toVoList(auditLogMapper.selectListByQuery(buildWrapper(query)));
    }

    @Override
    public PageData<LogVO> page(LogQuery query) {
        Page<LogEntity> page = auditLogMapper.paginate(PageUtils.toPage(query), buildWrapper(query));
        return PageUtils.toPageData(page, auditLogConvert::toVo);
    }

    @Override
    public LogVO getById(Long id) {
        LogVO vo = auditLogMapper.getById(id);
        if (vo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审计日志 {} 不存在", id);
        }
        return vo;
    }

    private QueryWrapper buildWrapper(LogQuery query) {
        QueryWrapper wrapper = QueryWrapper.create();
        // 用 TableDef 类型安全列名（APT 生成），替代裸字符串列名，
        // 列名重构时编译期报错而非运行期 SQL 失败
        LogEntityTableDef t = LogEntityTableDef.LOG_ENTITY;
        if (StrUtil.isNotBlank(query.getAction())) {
            wrapper.and(t.ACTION.like(escapeLike(query.getAction())));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            wrapper.and(t.CATEGORY.eq(query.getCategory()));
        }
        if (StrUtil.isNotBlank(query.getOperatorId())) {
            wrapper.and(t.OPERATOR_ID.like(escapeLike(query.getOperatorId())));
        }
        if (StrUtil.isNotBlank(query.getDescription())) {
            wrapper.and(t.DESCRIPTION.like(escapeLike(query.getDescription())));
        }
        if (StrUtil.isNotBlank(query.getSourceService())) {
            wrapper.and(t.SOURCE_SERVICE.eq(query.getSourceService()));
        }
        if (query.getSuccess() != null) {
            wrapper.and(t.SUCCESS.eq(query.getSuccess()));
        }
        if (query.getStartTime() != null) {
            wrapper.and(t.TIMESTAMP.ge(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            wrapper.and(t.TIMESTAMP.le(query.getEndTime()));
        }
        wrapper.orderBy(PageUtils.toOrderBy(query, DEFAULT_ORDER_BY));
        return wrapper;
    }

    /**
     * 转义 LIKE 查询值中的 SQL 通配符（{@code \%} {@code \_} {@code \\}）.
     *
     * <p>mybatis-flex 的 {@code like()} 把值拼进 {@code CONCAT('%', ?, '%')}，参数化已防
     * SQL 注入，但 {@code %}/{@code _} 作为 LIKE 通配符会让用户传 {@code %} 时匹配全表、
     * {@code _} 匹配任意单字符。这里前置转义为字面量，保证用户能按字面搜索含通配符的值.</p>
     */
    private static String escapeLike(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // 先转义反斜杠本身（避免把后续转义符再转义），再转义 % 和 _
        StringBuilder sb = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}

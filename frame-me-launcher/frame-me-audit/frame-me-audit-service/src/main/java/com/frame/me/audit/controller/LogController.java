package com.frame.me.audit.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageData;
import com.frame.me.audit.api.ILogApi;
import com.frame.me.audit.api.query.LogQuery;
import com.frame.me.audit.api.vo.LogVO;
import com.frame.me.audit.service.ILogService;
import com.frame.me.base.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计日志管理 Controller，实现 {@link ILogApi}.
 *
 * <p>只读查询端点（详情/分页/列表），由全局 {@code me.auth.enforce-login}
 * 强制登录保护；审计日志本身敏感，如需更细粒度管控可在本类叠加
 * {@code @SaCheckRole} 并配置 {@code me.auth.sa-token.users/roles}。</p>
 *
 * @author frame-me
 */
@RestController
@Validated
@RequiredArgsConstructor
public class LogController implements ILogApi {

    private final ILogService auditLogService;

    @Override
    public IResult<List<LogVO>> list(LogQuery query) {
        return Result.success(auditLogService.list(query));
    }

    @Override
    public IResult<PageData<LogVO>> page(LogQuery query) {
        return Result.success(auditLogService.page(query));
    }

    @Override
    public IResult<LogVO> getById(Long id) {
        return Result.success(auditLogService.getById(id));
    }
}

package com.frame.me.audit.api;

import com.frame.me.api.annotation.QueryMap;
import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageData;
import com.frame.me.audit.api.query.LogQuery;
import com.frame.me.audit.api.vo.LogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 审计日志管理 API 契约.
 *
 * <p>提供审计日志的详情、分页、列表查询，均支持按动作/分类/操作人/来源服务/
 * 成功与否/时间区间组合搜索。</p>
 *
 * @author frame-me
 */
@Tag(name = "审计日志管理", description = "审计日志详情、分页、列表查询")
@HttpExchange("/api/log")
public interface ILogApi {

    @Operation(summary = "查询列表", description = "按条件搜索审计日志列表（不分页，按事件发生时间倒序）")
    @GetExchange("/list")
    IResult<List<LogVO>> list(@Valid @QueryMap LogQuery query);

    @Operation(summary = "分页查询", description = "按条件搜索审计日志并分页返回")
    @GetExchange("/page")
    IResult<PageData<LogVO>> page(@Valid @QueryMap LogQuery query);

    @Operation(summary = "根据 ID 查询详情", description = "根据主键 ID 查询单条审计日志详情（mapper.xml 自定义 SQL）")
    @GetExchange("/{id}")
    IResult<LogVO> getById(@Parameter(description = "日志 ID", required = true) @Positive(message = "日志 ID 必须大于 0") @PathVariable Long id);
}

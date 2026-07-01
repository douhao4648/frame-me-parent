package com.frame.me.tester.api;

import com.frame.me.api.annotation.QueryMap;
import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageData;
import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.api.query.FlexDemoQuery;
import com.frame.me.tester.api.vo.FlexDemoVO;
import com.frame.me.validation.CreateGroup;
import com.frame.me.validation.UpdateGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * MyBatis-Flex 演示 API 契约.
 *
 * <p>演示基于 MyBatis-Flex 的增删改查，并通过 {@code /second}、{@code /count/{ds}} 接口演示
 * 结合 {@code frame-me-starter-dynamic-ds}（baomidou {@code @DS}）的多数据源切换。</p>
 */
@Tag(name = "Flex 演示", description = "MyBatis-Flex 增删改查 + 多数据源切换演示")
@HttpExchange("/api/flex/demo")
public interface IFlexDemoApi {

    @Operation(summary = "查询列表", description = "查询全部 Flex 演示数据")
    @GetExchange("/list")
    IResult<List<FlexDemoVO>> list();

    @Operation(summary = "分页查询", description = "根据姓名、年龄分页查询 Flex 演示数据")
    @GetExchange("/page")
    IResult<PageData<FlexDemoVO>> page(@Valid @QueryMap FlexDemoQuery query);

    @Operation(summary = "根据 ID 查询", description = "根据主键 ID 查询单条 Flex 演示数据")
    @GetExchange("/{id}")
    IResult<FlexDemoVO> getById(@Parameter(description = "数据 ID", required = true) @Positive(message = "数据 ID 必须大于 0") @PathVariable Long id);

    @Operation(summary = "新增", description = "向主数据源（master）新增一条 Flex 演示数据")
    @PostExchange
    IResult<Long> create(@Validated(CreateGroup.class) @RequestBody FlexDemoDTO dto);

    @Operation(summary = "新增到 second 数据源", description = "使用 @DS(\"second\") 切换到 second 数据源新增一条数据")
    @PostExchange("/second")
    IResult<Long> createInSecond(@Validated(CreateGroup.class) @RequestBody FlexDemoDTO dto);

    @Operation(summary = "根据 ID 更新", description = "根据主键 ID 更新 Flex 演示数据，需传入 version 用于乐观锁控制")
    @PutExchange("/{id}")
    IResult<Boolean> update(@Parameter(description = "数据 ID", required = true) @Positive(message = "数据 ID 必须大于 0") @PathVariable Long id, @Validated(UpdateGroup.class) @RequestBody FlexDemoDTO dto);

    @Operation(summary = "根据 ID 删除", description = "根据主键 ID 逻辑删除 Flex 演示数据")
    @DeleteExchange("/{id}")
    IResult<Boolean> delete(@Parameter(description = "数据 ID", required = true) @Positive(message = "数据 ID 必须大于 0") @PathVariable Long id);

    @Operation(summary = "统计指定数据源条数", description = "ds=second 时使用 @DS 切换到 second 数据源统计，否则统计 master")
    @GetExchange("/count/{ds}")
    IResult<Long> count(@Parameter(description = "数据源名称：master / second", required = true) @PathVariable String ds);
}

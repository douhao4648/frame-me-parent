package com.frame.me.tester.api;

import com.frame.me.api.annotation.QueryMap;
import com.frame.me.api.result.IResult;
import com.frame.me.api.result.PageResult;
import com.frame.me.tester.api.dto.DemoDTO;
import com.frame.me.tester.api.query.DemoComplexQuery;
import com.frame.me.tester.api.query.DemoQuery;
import com.frame.me.tester.api.vo.DemoComplexVO;
import com.frame.me.tester.api.vo.DemoVO;
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
import org.springframework.web.service.annotation.*;

import java.util.List;

/**
 * 演示数据 API 契约.
 */
@Tag(name = "演示数据", description = "演示用增删改查接口")
@HttpExchange("/api/demo")
public interface IDemoApi {

    /**
     * 查询演示数据列表.
     *
     * @return 演示数据列表
     */
    @Operation(summary = "查询列表", description = "查询全部演示数据列表")
    @GetExchange("/list")
    IResult<List<DemoVO>> list();

    /**
     * 分页查询演示数据.
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询", description = "根据姓名、年龄分页查询演示数据")
    @GetExchange("/page")
    IResult<PageResult<DemoVO>> page(@Valid @QueryMap DemoQuery query);

    /**
     * 复杂查询演示数据（手写 SQL）.
     *
     * @param query 复杂查询参数
     * @return 复杂查询结果列表
     */
    @Operation(summary = "复杂查询", description = "根据年龄范围、创建时间范围进行复杂查询")
    @GetExchange("/complex-list")
    IResult<List<DemoComplexVO>> complexList(@Valid @QueryMap DemoComplexQuery query);

    /**
     * 根据 ID 查询演示数据.
     *
     * @param id 数据 ID
     * @return 演示数据
     */
    @Operation(summary = "根据 ID 查询", description = "根据主键 ID 查询单条演示数据")
    @GetExchange("/{id}")
    IResult<DemoVO> getById(@Parameter(description = "数据 ID", required = true) @Positive(message = "数据 ID 必须大于 0") @PathVariable Long id);

    /**
     * 插入演示数据.
     *
     * @param dto 演示数据 DTO
     * @return 新增数据 ID
     */
    @Operation(summary = "新增", description = "新增一条演示数据")
    @PostExchange
    IResult<Long> create(@Validated(CreateGroup.class) @RequestBody DemoDTO dto);

    /**
     * 根据 ID 更新演示数据.
     *
     * @param id  数据 ID
     * @param dto 演示数据 DTO，需包含 version 用于乐观锁控制
     * @return 是否更新成功
     */
    @Operation(summary = "根据 ID 更新", description = "根据主键 ID 更新演示数据，需传入 version 用于乐观锁控制")
    @PutExchange("/{id}")
    IResult<Boolean> update(@Parameter(description = "数据 ID", required = true) @Positive(message = "数据 ID 必须大于 0") @PathVariable Long id, @Validated(UpdateGroup.class) @RequestBody DemoDTO dto);

    /**
     * 根据 ID 删除演示数据（逻辑删除）.
     *
     * @param id 数据 ID
     * @return 是否删除成功
     */
    @Operation(summary = "根据 ID 删除", description = "根据主键 ID 逻辑删除演示数据")
    @DeleteExchange("/{id}")
    IResult<Boolean> delete(@Parameter(description = "数据 ID", required = true) @Positive(message = "数据 ID 必须大于 0") @PathVariable Long id);
}

package com.frame.me.tester.api;

import com.frame.me.api.result.IResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

/**
 * 数据源切换演示 API 契约.
 */
@Tag(name = "数据源切换", description = "演示动态数据源切换")
@HttpExchange("/api/datasource")
public interface IDataSourceApi {

    /**
     * 返回主数据源 JDBC URL.
     *
     * @return 主数据源 JDBC URL
     */
    @Operation(summary = "主数据源", description = "返回当前主数据源的 JDBC URL")
    @GetExchange("/primary")
    IResult<String> primary();

    /**
     * 返回主数据源 JDBC URL.
     *
     * @return 主数据源 JDBC URL
     */
    @Operation(summary = "master数据源", description = "返回当前主数据源的 JDBC URL")
    @GetExchange("/master")
    IResult<String> master();

    /**
     * 使用 {@code @DS("second")} 切换到 second 数据源并返回其 JDBC URL.
     *
     * @return second 数据源 JDBC URL
     */
    @Operation(summary = "第二数据源", description = "使用 @DS(\"second\") 切换到 second 数据源并返回其 JDBC URL")
    @GetExchange("/second")
    IResult<String> second();

    /**
     * 返回当前所有数据源的实际连接池配置参数.
     *
     * @return 所有数据源连接池配置，key 为数据源名称
     */
    @Operation(summary = "所有数据源连接池配置", description = "返回当前所有数据源的实际连接池参数，支持 Hikari / Druid")
    @GetExchange("/pool-configs")
    IResult<Map<String, Map<String, Object>>> poolConfigs();
}

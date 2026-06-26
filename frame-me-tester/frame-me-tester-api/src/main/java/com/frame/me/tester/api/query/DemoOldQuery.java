//package com.frame.me.tester.api.query;
//
//import com.frame.me.adapter.api.query.PageParam;
//import io.swagger.v3.oas.annotations.media.Schema;
//import jakarta.validation.constraints.Max;
//import jakarta.validation.constraints.Min;
//import jakarta.validation.constraints.Size;
//import lombok.Data;
//import lombok.EqualsAndHashCode;
//
///**
// * 演示数据分页查询参数（老接口规范）.
// */
//@Data
//@EqualsAndHashCode(callSuper = true)
//@Schema(description = "演示数据分页查询参数（老规范）")
//public class DemoOldQuery extends PageParam {
//
//    /**
//     * 姓名，支持模糊查询.
//     */
//    @Size(max = 50, message = "姓名长度不能超过 50")
//    @Schema(description = "姓名，支持模糊查询")
//    private String name;
//
//    /**
//     * 年龄，精确查询.
//     */
//    @Min(value = 0, message = "年龄必须大于等于 0")
//    @Max(value = 150, message = "年龄不能超过 150")
//    @Schema(description = "年龄，精确查询")
//    private Integer age;
//}

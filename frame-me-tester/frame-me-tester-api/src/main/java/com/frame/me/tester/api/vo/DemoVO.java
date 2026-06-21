package com.frame.me.tester.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 演示数据返回 VO.
 */
@Data
@Schema(description = "演示数据返回 VO")
public class DemoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID.
     */
    @Schema(description = "ID")
    private Long id;

    /**
     * 姓名.
     */
    @Schema(description = "姓名")
    private String name;

    /**
     * 年龄.
     */
    @Schema(description = "年龄")
    private Integer age;

    /**
     * 版本号.
     */
    @Schema(description = "版本号")
    private Integer version;

    /**
     * 创建时间.
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间.
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

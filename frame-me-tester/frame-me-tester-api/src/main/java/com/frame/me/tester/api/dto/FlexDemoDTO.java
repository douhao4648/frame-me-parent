package com.frame.me.tester.api.dto;

import com.frame.me.validation.CreateGroup;
import com.frame.me.validation.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Flex 演示数据请求 DTO.
 */
@Data
@Schema(description = "Flex 演示数据请求 DTO")
public class FlexDemoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名.
     */
    @NotBlank(message = "姓名不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Size(max = 50, message = "姓名长度不能超过 50", groups = {CreateGroup.class, UpdateGroup.class})
    @Schema(description = "姓名")
    private String name;

    /**
     * 年龄.
     */
    @NotNull(message = "年龄不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Min(value = 0, message = "年龄必须大于等于 0", groups = {CreateGroup.class, UpdateGroup.class})
    @Max(value = 150, message = "年龄不能超过 150", groups = {CreateGroup.class, UpdateGroup.class})
    @Schema(description = "年龄")
    private Integer age;

    /**
     * 版本号，用于乐观锁控制.
     */
    @NotNull(message = "版本号不能为空", groups = UpdateGroup.class)
    @Min(value = 0, message = "版本号必须大于等于 0", groups = UpdateGroup.class)
    @Schema(description = "版本号，用于乐观锁控制")
    private Integer version;
}

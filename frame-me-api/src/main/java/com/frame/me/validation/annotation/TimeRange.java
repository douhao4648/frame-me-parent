package com.frame.me.validation.annotation;

import com.frame.me.validation.validator.TimeRangeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验对象的时间范围字段是否合法（开始时间 <= 结束时间）.
 *
 * <p>任一字段为空时不进行校验。</p>
 */
@Documented
@Constraint(validatedBy = TimeRangeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeRange {

    /**
     * 校验失败时的默认消息.
     */
    String message() default "开始时间不能晚于结束时间";

    /**
     * 开始时间字段名.
     */
    String startField() default "startTime";

    /**
     * 结束时间字段名.
     */
    String endField() default "endTime";

    /**
     * 校验分组.
     */
    Class<?>[] groups() default {};

    /**
     * 负载.
     */
    Class<? extends Payload>[] payload() default {};
}

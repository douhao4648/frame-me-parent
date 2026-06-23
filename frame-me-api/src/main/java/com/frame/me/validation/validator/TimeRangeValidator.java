package com.frame.me.validation.validator;

import com.frame.me.validation.annotation.TimeRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.temporal.Temporal;

/**
 * {@link TimeRange} 校验器实现.
 */
public class TimeRangeValidator implements ConstraintValidator<TimeRange, Object> {

    private String startField;

    private String endField;

    @Override
    public void initialize(TimeRange constraintAnnotation) {
        this.startField = constraintAnnotation.startField();
        this.endField = constraintAnnotation.endField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Temporal start = getField(value, startField);
        Temporal end = getField(value, endField);
        if (start == null || end == null) {
            return true;
        }
        // Temporal 不一定可比较，统一按 Comparable 处理
        return compare(start, end) <= 0;
    }

    @SuppressWarnings("unchecked")
    private int compare(Temporal start, Temporal end) {
        if (start instanceof Comparable && end instanceof Comparable
                && start.getClass().isAssignableFrom(end.getClass())) {
            return ((Comparable<Object>) start).compareTo(end);
        }
        return 0;
    }

    private Temporal getField(Object target, String fieldName) {
        try {
            Object result = target.getClass().getMethod(fieldName).invoke(target);
            if (result instanceof Temporal temporal) {
                return temporal;
            }
        } catch (Exception e) {
            // 字段不存在或不可读，忽略校验
        }
        return null;
    }
}

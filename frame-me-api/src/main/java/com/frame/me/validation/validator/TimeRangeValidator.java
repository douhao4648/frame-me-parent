package com.frame.me.validation.validator;

import com.frame.me.validation.annotation.TimeRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;
import java.time.temporal.Temporal;

/**
 * {@link TimeRange} 校验器实现.
 *
 * <p>字段值按以下优先级解析：JavaBean getter（{@code getXxx} / {@code isXxx}）→
 * 同名无参方法（record / 流式访问器）→ 声明字段直读（含父类）。
 * 字段不存在或值为空时不校验（与注解契约一致）。</p>
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
        Temporal start = getFieldValue(value, startField);
        Temporal end = getFieldValue(value, endField);
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

    private Temporal getFieldValue(Object target, String fieldName) {
        Object result = invokeAccessor(target, fieldName);
        if (result == null) {
            result = readDeclaredField(target, fieldName);
        }
        return result instanceof Temporal temporal ? temporal : null;
    }

    /**
     * 依次尝试 getXxx / isXxx / 同名无参方法（record 访问器）.
     */
    private Object invokeAccessor(Object target, String fieldName) {
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String[] candidates = {"get" + suffix, "is" + suffix, fieldName};
        for (String methodName : candidates) {
            try {
                return target.getClass().getMethod(methodName).invoke(target);
            } catch (NoSuchMethodException e) {
                // 尝试下一种访问器形态
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 声明字段直读，沿类层次向上查找.
     */
    private Object readDeclaredField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

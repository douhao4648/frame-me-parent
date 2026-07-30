package com.frame.me.validation.validator;

import com.frame.me.validation.annotation.TimeRange;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TimeRangeValidator} 单元测试.
 *
 * @author frame-me
 */
class TimeRangeValidatorTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 2, 0, 0);

    /**
     * Lombok/@Data 风格的 JavaBean getter（getXxx）必须能取到字段 —— 修复前
     * 按原始字段名 getMethod 永远匹配不上，校验静默通过.
     */
    @Test
    void shouldValidateBeanStyleAccessors() {
        TimeRangeValidator validator = validatorFor(BeanStyle.class);

        assertThat(validator.isValid(new BeanStyle(T1, T2), null)).isTrue();
        assertThat(validator.isValid(new BeanStyle(T2, T1), null)).isFalse();
        assertThat(validator.isValid(new BeanStyle(T1, T1), null)).isTrue();
    }

    /**
     * record 的同名访问器保持兼容（修复前唯一碰巧生效的形态）.
     */
    @Test
    void shouldValidateRecordStyleAccessors() {
        TimeRangeValidator validator = validatorFor(RecordStyle.class);

        assertThat(validator.isValid(new RecordStyle(T1, T2), null)).isTrue();
        assertThat(validator.isValid(new RecordStyle(T2, T1), null)).isFalse();
    }

    /**
     * 无 getter 时回退声明字段直读，并支持自定义字段名.
     */
    @Test
    void shouldFallbackToDeclaredFieldRead() {
        TimeRangeValidator validator = validatorFor(FieldOnly.class);

        assertThat(validator.isValid(new FieldOnly(T1, T2), null)).isTrue();
        assertThat(validator.isValid(new FieldOnly(T2, T1), null)).isFalse();
    }

    /**
     * 任一字段为空或字段不存在时不校验（注解契约）.
     */
    @Test
    void shouldPassWhenFieldNullOrMissing() {
        TimeRangeValidator validator = validatorFor(BeanStyle.class);

        assertThat(validator.isValid(new BeanStyle(null, T2), null)).isTrue();
        assertThat(validator.isValid(new BeanStyle(T1, null), null)).isTrue();
        assertThat(validator.isValid(new Object(), null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }

    private TimeRangeValidator validatorFor(Class<?> type) {
        TimeRangeValidator validator = new TimeRangeValidator();
        validator.initialize(type.getAnnotation(TimeRange.class));
        return validator;
    }

    @Data
    @TimeRange
    static class BeanStyle {
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
    }

    @TimeRange
    record RecordStyle(LocalDateTime startTime, LocalDateTime endTime) {
    }

    @TimeRange(startField = "from", endField = "to")
    static class FieldOnly {
        private final LocalDateTime from;
        private final LocalDateTime to;

        FieldOnly(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
        }
    }
}

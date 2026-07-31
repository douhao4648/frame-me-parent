package com.frame.me.validation.validator;

import com.frame.me.validation.annotation.TimeRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link TimeRange} 校验器实现.
 *
 * <p>字段值按以下优先级解析：JavaBean getter（{@code getXxx} / {@code isXxx}）→
 * 同名无参方法（record / 流式访问器）→ 声明字段直读（含父类）。
 * 字段不存在或值为空时不校验（与注解契约一致）。</p>
 *
 * <p>反射结果按 {@code Class + fieldName} 缓存（{@link #ACCESSOR_CACHE} / {@link #FIELD_CACHE}），
 * 避免高频校验场景每次 {@code getMethod} / {@code getDeclaredField} 的反射查找开销.</p>
 *
 * <p>缓存 key 用 {@link Class} 对象（而非类名字符串）参与判定：devtools 热重启时业务 DTO 由
 * restart ClassLoader 重新加载（同名不同 Class），用 Class 同一性作 key 可避免命中旧 Class 的
 * Method 导致 {@code invoke} 抛 {@link IllegalArgumentException} 被吞、校验静默失效（fail-open）.</p>
 */
public class TimeRangeValidator implements ConstraintValidator<TimeRange, Object> {

    /** 缓存 key：{@code Class} 同一性区分不同 ClassLoader 加载的同名类，field 定位具体字段. */
    private record CacheKey(Class<?> type, String field) {
    }

    /** 访问器方法缓存：value = 解析到的 Method. */
    private static final ConcurrentHashMap<CacheKey, Method> ACCESSOR_CACHE = new ConcurrentHashMap<>();
    /** 字段缓存：value = 解析到的 Field. */
    private static final ConcurrentHashMap<CacheKey, Field> FIELD_CACHE = new ConcurrentHashMap<>();

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
     * 依次尝试 getXxx / isXxx / 同名无参方法（record 访问器），反射结果按 Class+field 缓存.
     */
    private Object invokeAccessor(Object target, String fieldName) {
        CacheKey cacheKey = new CacheKey(target.getClass(), fieldName);
        Method cached = ACCESSOR_CACHE.get(cacheKey);
        if (cached != null) {
            try {
                return cached.invoke(target);
            } catch (Exception e) {
                return null;
            }
        }
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String[] candidates = {"get" + suffix, "is" + suffix, fieldName};
        for (String methodName : candidates) {
            try {
                Method method = target.getClass().getMethod(methodName);
                ACCESSOR_CACHE.putIfAbsent(cacheKey, method);
                return method.invoke(target);
            } catch (NoSuchMethodException e) {
                // 尝试下一种访问器形态
            } catch (Exception e) {
                return null;
            }
        }
        // 全部访问器不存在：缓存 ABSENT 标记（用占位 Method 避免重复解析）
        // ponytail: 用 null 值缓存区分较复杂，此处保持未缓存，下次仍尝试（访问器不存在的场景罕见）
        return null;
    }

    /**
     * 声明字段直读，沿类层次向上查找，反射结果按 Class+field 缓存.
     */
    private Object readDeclaredField(Object target, String fieldName) {
        CacheKey cacheKey = new CacheKey(target.getClass(), fieldName);
        Field cached = FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            try {
                return cached.get(target);
            } catch (Exception e) {
                return null;
            }
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                FIELD_CACHE.putIfAbsent(cacheKey, field);
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


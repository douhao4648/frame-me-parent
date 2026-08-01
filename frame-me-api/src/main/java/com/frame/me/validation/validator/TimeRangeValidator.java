package com.frame.me.validation.validator;

import com.frame.me.validation.annotation.TimeRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
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

    private static final Logger log = System.getLogger(TimeRangeValidator.class.getName());

    /** 缓存 key：{@code Class} 同一性区分不同 ClassLoader 加载的同名类，field 定位具体字段. */
    private record CacheKey(Class<?> type, String field) {
    }

    /** 占位 Method，标记该字段无可用访问器，避免重复反射查找. */
    private static final Method ABSENT;

    /** 占位 Field，标记该字段在类层次中未找到，避免重复遍历继承链. */
    private static final Field ABSENT_FIELD;

    static {
        try {
            ABSENT = Object.class.getMethod("toString");
            ABSENT_FIELD = TimeRangeValidator.class.getDeclaredField("startField");
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 访问器方法缓存：value = 解析到的 Method（含 {@link #ABSENT} 占位）. */
    private static final ConcurrentHashMap<CacheKey, Method> ACCESSOR_CACHE = new ConcurrentHashMap<>();
    /** 字段缓存：value = 解析到的 Field（含 {@link #ABSENT_FIELD} 占位）. */
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
                && start.getClass().equals(end.getClass())) {
            return ((Comparable<Object>) start).compareTo(end);
        }
        log.log(Level.WARNING, "无法比较不同类型的时间字段: start={0}({1}), end={2}({3})",
                start, start.getClass().getName(), end, end.getClass().getName());
        return -1;
    }

    private Temporal getFieldValue(Object target, String fieldName) {
        Object result = invokeAccessor(target, fieldName);
        if (!(result instanceof Temporal)) {
            result = readDeclaredField(target, fieldName);
        }
        return result instanceof Temporal temporal ? temporal : null;
    }

    /**
     * 依次尝试 getXxx / isXxx / 同名无参方法（record 访问器），反射结果按 Class+field 缓存.
     *
     * <p>若三种访问器均不存在，缓存 {@link #ABSENT} 占位标记，后续相同 key 直接返回 null，
     * 避免高频校验场景下的重复反射扫描。</p>
     */
    private Object invokeAccessor(Object target, String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        CacheKey cacheKey = new CacheKey(target.getClass(), fieldName);
        Method cached = ACCESSOR_CACHE.get(cacheKey);
        if (cached != null) {
            if (cached == ABSENT) {
                return null;
            }
            try {
                return cached.invoke(target);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                log.log(Level.WARNING, "调用缓存的访问器方法失败: " + cacheKey, e);
                return null;
            } catch (IllegalAccessException e) {
                log.log(Level.WARNING, "无法访问缓存的访问器方法: " + cacheKey, e);
                return null;
            }
        }
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String[] candidates = {"get" + suffix, "is" + suffix, fieldName};
        for (String methodName : candidates) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object result = method.invoke(target);
                // 调用成功后再缓存，避免将抛出 checked exception 的 broken getter 缓存后跳过备选
                ACCESSOR_CACHE.putIfAbsent(cacheKey, method);
                return result;
            } catch (NoSuchMethodException e) {
                // 尝试下一种访问器形态
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                log.log(Level.WARNING, "调用访问器方法失败: " + cacheKey + "." + methodName, e);
                return null;
            } catch (IllegalAccessException e) {
                log.log(Level.WARNING, "无法访问方法: " + cacheKey + "." + methodName, e);
                return null;
            }
        }
        // 全部访问器不存在：缓存 ABSENT 标记，避免重复反射解析
        ACCESSOR_CACHE.putIfAbsent(cacheKey, ABSENT);
        return null;
    }

    /**
     * 声明字段直读，沿类层次向上查找，反射结果按 Class+field 缓存.
     */
    private Object readDeclaredField(Object target, String fieldName) {
        CacheKey cacheKey = new CacheKey(target.getClass(), fieldName);
        Field cached = FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            if (cached == ABSENT_FIELD) {
                return null;
            }
            try {
                return cached.get(target);
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "读取缓存的字段值失败: " + cacheKey, e);
                return null;
            } catch (IllegalAccessException e) {
                log.log(Level.WARNING, "无法访问缓存的字段: " + cacheKey, e);
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
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "读取字段值失败: " + cacheKey + "." + fieldName, e);
                return null;
            } catch (IllegalAccessException e) {
                log.log(Level.WARNING, "无法访问字段: " + cacheKey + "." + fieldName, e);
                return null;
            }
        }
        // 字段在整条继承链中均不存在：缓存占位标记，避免重复遍历
        FIELD_CACHE.putIfAbsent(cacheKey, ABSENT_FIELD);
        return null;
    }

    /**
     * 清理访问器和字段缓存，释放 ClassLoader 引用.
     *
     * <p>在应用上下文关闭（如 devtools 热重启）时调用，避免静态缓存
     * 持有旧 ClassLoader 的 Class 对象导致 ClassLoader 无法 GC。</p>
     */
    public static void cleanup() {
        ACCESSOR_CACHE.clear();
        FIELD_CACHE.clear();
    }
}

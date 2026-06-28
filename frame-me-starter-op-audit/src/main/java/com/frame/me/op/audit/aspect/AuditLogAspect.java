package com.frame.me.op.audit.aspect;

import com.alibaba.fastjson2.JSON;
import com.frame.me.op.audit.annotation.AuditLog;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import com.frame.me.op.audit.spi.AuditLogOperatorSupplier;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.base.event.EventBridgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计日志切面.
 *
 * <p>拦截标记了 {@link AuditLog} 的方法，收集动作、参数、返回值、异常、耗时等信息，
 * 并发布 {@link AuditLogEvent}。</p>
 *
 * @author frame-me
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\#([a-zA-Z_][\\w.]*)");

    private final EventBridgePublisher publisher;
    private final AuditLogOperatorSupplier operatorSupplier;
    private final AuditProperties properties;
    private final EventBridgeProperties eventBridgeProperties;

    /**
     * 拦截 {@link AuditLog} 注解方法.
     *
     * @param point     连接点
     * @param auditLog  注解
     * @return 方法返回值
     * @throws Throwable 方法抛出的异常
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint point, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Method method = resolveTargetMethod(point);
        Object[] args = point.getArgs();

        AuditLogRecord record = new AuditLogRecord();
        record.setAction(resolveAction(auditLog, point));
        record.setCategory(auditLog.category());
        record.setOperatorId(operatorSupplier.getOperatorId());
        record.setTimestamp(Instant.now());
        record.setSourceService(eventBridgeProperties.getServiceName());
        record.setTargetService(properties.getTargetService());

        if (auditLog.recordParams()) {
            record.setParams(serializeParams(method, args));
        }

        Object result = null;
        Throwable error = null;
        boolean success = false;

        try {
            result = point.proceed();
            success = true;
            if (auditLog.recordResult()) {
                record.setResult(serialize(result));
            }
        } catch (Throwable t) {
            error = t;
            success = false;
            if (auditLog.recordError()) {
                record.setErrorMsg(t.getMessage());
            }
        } finally {
            record.setDurationMs(System.currentTimeMillis() - start);
            record.setSuccess(success);
            record.setDescription(resolveDescription(auditLog.description(), method, args, result, error));
            publish(record);
        }

        if (error != null) {
            throw error;
        }
        return result;
    }

    private String resolveAction(AuditLog auditLog, ProceedingJoinPoint point) {
        if (auditLog.action() != null && !auditLog.action().isEmpty()) {
            return auditLog.action();
        }
        return point.getSignature().getDeclaringTypeName() + "#" + point.getSignature().getName();
    }

    private Method resolveTargetMethod(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        if (method.getParameterCount() == 0) {
            return method;
        }
        if (hasParameterNames(method)) {
            return method;
        }
        Class<?> targetClass = point.getTarget().getClass();
        // CGLIB 代理：在父类中查找带参数名的原始方法
        Class<?> clazz = targetClass.getSuperclass();
        while (clazz != null && clazz != Object.class) {
            try {
                Method targetMethod = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (hasParameterNames(targetMethod)) {
                    return targetMethod;
                }
            } catch (NoSuchMethodException e) {
                // ignore
            }
            clazz = clazz.getSuperclass();
        }
        // JDK 动态代理：在实现的接口中查找
        for (Class<?> iface : targetClass.getInterfaces()) {
            try {
                Method targetMethod = iface.getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (hasParameterNames(targetMethod)) {
                    return targetMethod;
                }
            } catch (NoSuchMethodException e) {
                // ignore
            }
        }
        return method;
    }

    private boolean hasParameterNames(Method method) {
        Parameter[] parameters = method.getParameters();
        return parameters.length == 0 || parameters[0].isNamePresent();
    }

    private String[] getParameterNames(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            names[i] = parameters[i].getName();
        }
        return names;
    }

    private void publish(AuditLogRecord record) {
        try {
            publisher.publish(new AuditLogEvent(record.getSourceService(), record, record.getTargetService()));
        } catch (Exception e) {
            log.error("发布审计日志事件失败: action={}", record.getAction(), e);
        }
    }

    private String serializeParams(Method method, Object[] args) {
        Map<String, Object> paramMap = new LinkedHashMap<>();
        String[] names = getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            String name = i < names.length ? names[i] : "arg" + i;
            paramMap.put(name, args[i]);
        }
        return truncate(serialize(paramMap));
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(value);
        } catch (Exception e) {
            log.warn("审计日志序列化失败", e);
            return "[serialize-error]";
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        int max = properties.getMaxParamLength();
        if (max <= 0) {
            return value;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= max) {
            return value;
        }
        int idx = max;
        while (idx > 0 && (bytes[idx] & 0xC0) == 0x80) {
            idx--;
        }
        return new String(bytes, 0, idx, StandardCharsets.UTF_8) + "...";
    }

    private String resolveDescription(String description, Method method, Object[] args,
                                      Object result, Throwable error) {
        if (description == null || description.isEmpty() || !description.contains("#")) {
            return description;
        }

        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        String[] names = getParameterNames(method);
        for (int i = 0; i < names.length && i < args.length; i++) {
            context.setVariable(names[i], args[i]);
        }
        for (int i = 0; i < args.length; i++) {
            context.setVariable("arg" + i, args[i]);
        }
        context.setVariable("result", result);
        context.setVariable("error", error);

        StringBuffer sb = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(description);
        while (matcher.find()) {
            String expression = matcher.group(1);
            try {
                Object value = SPEL_PARSER.parseExpression("#" + expression).getValue(context);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            } catch (Exception e) {
                log.warn("审计日志描述占位符解析失败: expression={}", expression, e);
                matcher.appendReplacement(sb, Matcher.quoteReplacement("#" + expression));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

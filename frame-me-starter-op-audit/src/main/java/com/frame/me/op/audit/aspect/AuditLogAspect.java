package com.frame.me.op.audit.aspect;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.frame.me.op.audit.annotation.AuditLog;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import com.frame.me.op.audit.spi.IAuditLogOperatorSupplier;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.base.event.EventBridgeProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
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
    /** 标记已 warn 过参数名缺失，避免刷屏. */
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED_MISSING_PARAMETERS = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final ApplicationEventPublisher localPublisher;
    private final ObjectProvider<EventBridgePublisher> bridgePublisherProvider;
    private final IAuditLogOperatorSupplier operatorSupplier;
    private final AuditProperties properties;
    private final EventBridgeProperties eventBridgeProperties;

    /**
     * 异步发布执行器：同步模式为 null（直接 publish），异步模式为有界线程池.
     */
    private TaskExecutor publishExecutor;

    @PostConstruct
    void initPublishExecutor() {
        if (!properties.getAsync().isEnabled()) {
            return;
        }
        AuditProperties.Async cfg = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix("audit-publish-");
        // 队列满时丢弃审计（Discard），不阻塞业务线程
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        this.publishExecutor = executor;
        log.info("AuditLogAspect 异步发布已启用: core={}, max={}, queue={}",
                cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity());
    }

    @PreDestroy
    void shutdownPublishExecutor() {
        if (publishExecutor instanceof ThreadPoolTaskExecutor tpte) {
            tpte.shutdown();
            try {
                if (!tpte.getThreadPoolExecutor().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("审计异步线程池等待超时，部分审计事件可能未发布");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("审计异步线程池关闭被中断");
            }
        }
    }

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
        // 操作人 SPI 在 try 块外：异常不得阻断被审计的业务方法，降级为 anonymous.
        try {
            record.setOperatorId(operatorSupplier.getOperatorId());
        } catch (Exception e) {
            log.warn("审计操作人 SPI 取值失败，降级为 anonymous: action={}", record.getAction(), e);
            record.setOperatorId("anonymous");
        }
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
                record.setResult(truncate(serialize(result)));
            }
        } catch (Throwable t) {
            error = t;
            success = false;
            if (auditLog.recordError()) {
                record.setErrorMsg(truncate(t.getMessage()));
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
        // 检测未编译 -parameters：参数名回退为 arg0/arg1，审计日志参数名无意义，warn 一次提示
        if (parameters.length > 0 && !parameters[0].isNamePresent() && WARNED_MISSING_PARAMETERS.compareAndSet(false, true)) {
            log.warn("审计日志参数名缺失（编译未启用 -parameters），审计日志参数将显示为 arg0/arg1。"
                    + "请在 maven-compiler-plugin 配置 <parameters>true</parameters> 启用参数名");
        }
        return names;
    }

    private void publish(AuditLogRecord record) {
        // 异步模式：提交到专用线程池，序列化已在业务线程完成（args/result 在方法返回后失效），
        // 仅 publish（含 HTTP transport）异步化，队列满时丢弃审计（不阻塞业务）.
        if (publishExecutor != null) {
            try {
                publishExecutor.execute(() -> doPublish(record));
            } catch (RejectedExecutionException e) {
                // DiscardPolicy 不抛 RejectedExecutionException，但兜底防御
                log.warn("审计事件队列满，丢弃: action={}", record.getAction());
            }
            return;
        }
        // 同步模式：直接 publish（阻塞业务线程，但审计不丢）
        doPublish(record);
    }

    private void doPublish(AuditLogRecord record) {
        try {
            AuditLogEvent event = new AuditLogEvent(record.getSourceService(), record,
                    record.getTargetService(), eventBridgeProperties.getInstanceId());
            EventBridgePublisher bridge = bridgePublisherProvider.getIfAvailable();
            if (bridge != null) {
                bridge.publish(event);
            } else {
                // me.event-bridge.enabled=false 时无桥接发布器：降级为仅本地发布，
                // 本进程 @EventListener（如 AuditLogLogger）照常消费，审计中心收不到
                log.debug("EventBridge 未启用，审计事件仅本地发布: action={}", record.getAction());
                localPublisher.publishEvent(event);
            }
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
            // ReferenceDetection：处理循环引用（自引用实体），避免递归遍历至 StackOverflowError
            // IgnoreErrorGetter：getter 抛异常时跳过该字段而非整体失败（如 Hibernate 懒加载代理）
            return JSON.toJSONString(value,
                    JSONWriter.Feature.ReferenceDetection,
                    JSONWriter.Feature.IgnoreErrorGetter);
        } catch (StackOverflowError e) {
            // 极端循环引用即便 ReferenceDetection 也兜不住（如自定义反序列化），降级而非逃逸
            log.warn("审计日志序列化栈溢出（疑似深层循环引用），降级为占位符");
            return "[serialize-overflow]";
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

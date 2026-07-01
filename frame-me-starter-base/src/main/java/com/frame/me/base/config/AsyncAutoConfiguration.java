package com.frame.me.base.config;

import com.frame.me.base.notify.INotifySender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务自动配置.
 *
 * <p>启用 {@link EnableAsync @Async} 支持，并提供默认线程池与未捕获异常处理器。
 * 用户可通过 {@code me.async.*} 覆盖默认行为，或声明自定义 {@link ThreadPoolTaskExecutor}
 * / {@link AsyncConfigurer} 完全接管。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
// 在 Spring Boot 默认 TaskExecutionAutoConfiguration 之前创建线程池，避免其先创建默认 Executor 导致本配置被跳过
@AutoConfigureBefore(TaskExecutionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "me.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncAutoConfiguration {

    /**
     * 创建默认异步线程池.
     *
     * @param properties 异步配置属性
     * @return 默认线程池
     */
    @Bean
    @ConditionalOnMissingBean(ThreadPoolTaskExecutor.class)
    public ThreadPoolTaskExecutor taskExecutor(AsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setAllowCoreThreadTimeOut(properties.isAllowCoreThreadTimeOut());
        if (properties.getAwaitTerminationSeconds() > 0) {
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        }
        executor.setRejectedExecutionHandler(resolveRejectionPolicy(properties.getRejectionPolicy()));
        executor.initialize();
        return executor;
    }

    /**
     * 注册默认 {@link AsyncConfigurer}，将默认线程池设为 {@code @Async} 默认执行器.
     *
     * @param taskExecutor 异步线程池
     * @param properties   异步配置属性
     * @param senders      通知发送器，可能为空
     * @return 默认 AsyncConfigurer
     */
    @Bean
    @ConditionalOnMissingBean(AsyncConfigurer.class)
    public AsyncConfigurer asyncConfigurer(ThreadPoolTaskExecutor taskExecutor,
                                           AsyncProperties properties,
                                           ObjectProvider<INotifySender> senders) {
        return new AsyncConfigurer() {

            @Override
            public Executor getAsyncExecutor() {
                return taskExecutor;
            }

            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                if (!properties.isExceptionHandlerEnabled()) {
                    return null;
                }
                return new DefaultAsyncUncaughtExceptionHandler(properties, senders);
            }
        };
    }

    /**
     * 根据配置字符串解析拒绝策略.
     *
     * @param policy 策略名称
     * @return 对应拒绝策略
     */
    private static RejectedExecutionHandler resolveRejectionPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        return switch (policy.toUpperCase()) {
            case "ABORT" -> new ThreadPoolExecutor.AbortPolicy();
            case "DISCARD" -> new ThreadPoolExecutor.DiscardPolicy();
            case "DISCARD_OLDEST" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
    }

    /**
     * 默认异步未捕获异常处理器.
     */
    private static class DefaultAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

        private final AsyncProperties properties;
        private final ObjectProvider<INotifySender> senders;

        DefaultAsyncUncaughtExceptionHandler(AsyncProperties properties,
                                             ObjectProvider<INotifySender> senders) {
            this.properties = properties;
            this.senders = senders;
        }

        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
            String className = method.getDeclaringClass().getName();
            String methodName = method.getName();
            log.error("异步方法执行异常: {}.{}，参数: {}", className, methodName, params, throwable);

            if (!properties.isExceptionNotifyEnabled()) {
                return;
            }
            // 优先使用 me.async.exception-notify-receivers；为空时由 INotifySender 实现
            // 回退到 me.notify.global-receivers（见 frame-me-starter-msg-notify）。
            List<String> receivers = properties.getExceptionNotifyReceivers();
            senders.ifAvailable(sender -> sender.send(
                    "异步方法执行异常",
                    buildExceptionContent(className, methodName, throwable),
                    receivers));
        }

        private static String buildExceptionContent(String className, String methodName, Throwable throwable) {
            StringWriter writer = new StringWriter();
            writer.write("类：");
            writer.write(className);
            writer.write("\n方法：");
            writer.write(methodName);
            writer.write("\n异常：");
            writer.write(throwable.getClass().getName());
            writer.write("\n消息：");
            writer.write(String.valueOf(throwable.getMessage()));
            writer.write("\n堆栈：\n");
            throwable.printStackTrace(new PrintWriter(writer));
            return writer.toString();
        }
    }
}

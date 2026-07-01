package com.frame.me.base.config;

import com.frame.me.base.notify.INotifySender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * 调度任务自动配置.
 *
 * <p>启用 {@link EnableScheduling @Scheduled} 支持，并提供默认调度线程池。
 * 用户可通过 {@code me.scheduling.*} 覆盖默认行为，或声明自定义 {@link TaskScheduler}
 * / {@link ThreadPoolTaskScheduler} 完全接管。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
// 在 Spring Boot 默认 TaskSchedulingAutoConfiguration 之前创建线程池，避免其先创建默认 Scheduler 导致本配置被跳过
@AutoConfigureBefore(TaskSchedulingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "me.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingAutoConfiguration {

    /**
     * 创建默认调度线程池.
     *
     * @param properties 调度配置属性
     * @param senders    通知发送器，可能为空
     * @return 默认调度线程池
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public ThreadPoolTaskScheduler taskScheduler(SchedulingProperties properties,
                                                 ObjectProvider<INotifySender> senders) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize());
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(properties.isRemoveOnCancelPolicy());
        if (properties.getAwaitTerminationSeconds() > 0) {
            scheduler.setWaitForTasksToCompleteOnShutdown(true);
            scheduler.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        }
        if (properties.isExceptionHandlerEnabled()) {
            scheduler.setErrorHandler(new DefaultSchedulingErrorHandler(properties, senders));
        }
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 默认调度异常处理器.
     */
    private static class DefaultSchedulingErrorHandler implements ErrorHandler {

        private final SchedulingProperties properties;
        private final ObjectProvider<INotifySender> senders;

        DefaultSchedulingErrorHandler(SchedulingProperties properties,
                                      ObjectProvider<INotifySender> senders) {
            this.properties = properties;
            this.senders = senders;
        }

        private static String extractLocation(Throwable throwable) {
            StackTraceElement[] elements = throwable.getStackTrace();
            for (int i = 0; i < elements.length - 1; i++) {
                if ("java.lang.reflect.Method".equals(elements[i].getClassName())
                        && "invoke".equals(elements[i].getMethodName())) {
                    StackTraceElement target = elements[i + 1];
                    return target.getClassName() + "." + target.getMethodName();
                }
            }
            for (StackTraceElement element : elements) {
                String className = element.getClassName();
                if (!className.startsWith("org.springframework.")
                        && !className.startsWith("java.")
                        && !className.startsWith("javax.")
                        && !className.startsWith("jakarta.")
                        && !className.startsWith("jdk.")
                        && !className.startsWith("sun.")) {
                    return className + "." + element.getMethodName();
                }
            }
            return "unknown";
        }

        private static String buildExceptionContent(String location, Throwable throwable) {
            StringWriter writer = new StringWriter();
            writer.write("位置：");
            writer.write(location);
            writer.write("\n异常：");
            writer.write(throwable.getClass().getName());
            writer.write("\n消息：");
            writer.write(String.valueOf(throwable.getMessage()));
            writer.write("\n堆栈：\n");
            throwable.printStackTrace(new PrintWriter(writer));
            return writer.toString();
        }

        @Override
        public void handleError(Throwable throwable) {
            String location = extractLocation(throwable);
            log.error("调度任务执行异常: {}", location, throwable);

            if (!properties.isExceptionNotifyEnabled()) {
                return;
            }
            // 优先使用 me.scheduling.exception-notify-receivers；为空时由 INotifySender 实现
            // 回退到 me.notify.global-receivers（见 frame-me-starter-msg-notify）。
            List<String> receivers = properties.getExceptionNotifyReceivers();
            senders.ifAvailable(sender -> sender.send(
                    "调度任务执行异常",
                    buildExceptionContent(location, throwable),
                    receivers));
        }
    }
}

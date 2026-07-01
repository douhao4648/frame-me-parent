package com.frame.me.base.config;

import com.frame.me.base.notify.INotifySender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AsyncAutoConfiguration} 测试.
 */
class AsyncAutoConfigurationTest {

    @Test
    void shouldSendExceptionNotifyWhenSenderAvailable() throws NoSuchMethodException {
        AsyncProperties properties = new AsyncProperties();
        properties.setExceptionNotifyEnabled(true);
        properties.setExceptionNotifyReceivers(List.of("dev@example.com"));

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();

        INotifySender sender = mock(INotifySender.class);
        when(sender.send(anyString(), anyString(), anyList())).thenReturn(true);
        ObjectProvider<INotifySender> senderProvider = new ObjectProvider<>() {
            @Override
            public INotifySender getIfAvailable() {
                return sender;
            }
        };

        AsyncAutoConfiguration configuration = new AsyncAutoConfiguration();
        AsyncConfigurer asyncConfigurer = configuration.asyncConfigurer(executor, properties, senderProvider);
        AsyncUncaughtExceptionHandler handler = asyncConfigurer.getAsyncUncaughtExceptionHandler();

        Method method = TestService.class.getMethod("asyncMethod");
        RuntimeException exception = new RuntimeException("test error");
        handler.handleUncaughtException(exception, method);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List> receiversCaptor = ArgumentCaptor.forClass(List.class);
        verify(sender).send(eq("异步方法执行异常"), contentCaptor.capture(), receiversCaptor.capture());
        assertThat(contentCaptor.getValue())
                .contains("类：" + TestService.class.getName())
                .contains("方法：asyncMethod")
                .contains("异常：" + RuntimeException.class.getName())
                .contains("消息：test error");
        assertThat(receiversCaptor.getValue()).containsExactly("dev@example.com");
    }

    @Test
    void shouldNotSendExceptionNotifyWhenDisabled() throws NoSuchMethodException {
        AsyncProperties properties = new AsyncProperties();
        properties.setExceptionNotifyEnabled(false);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();

        INotifySender sender = mock(INotifySender.class);
        ObjectProvider<INotifySender> senderProvider = new ObjectProvider<>() {
            @Override
            public INotifySender getIfAvailable() {
                return sender;
            }
        };

        AsyncAutoConfiguration configuration = new AsyncAutoConfiguration();
        AsyncConfigurer asyncConfigurer = configuration.asyncConfigurer(executor, properties, senderProvider);
        AsyncUncaughtExceptionHandler handler = asyncConfigurer.getAsyncUncaughtExceptionHandler();

        Method method = TestService.class.getMethod("asyncMethod");
        handler.handleUncaughtException(new RuntimeException("test"), method);

        verify(sender, never()).send(anyString(), anyString(), anyList());
    }

    static class TestService {
        public void asyncMethod() {
        }
    }
}

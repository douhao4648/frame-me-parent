package com.frame.me.tester.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 测试用异步服务.
 */
@Service
public class AsyncDemoService {

    @Async
    public CompletableFuture<String> asyncThreadName() {
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }
}

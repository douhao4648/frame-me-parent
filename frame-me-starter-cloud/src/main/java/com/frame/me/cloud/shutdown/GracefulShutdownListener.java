package com.frame.me.cloud.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * 优雅下线兜底监听器：监听 {@link ContextClosedEvent}.
 *
 * <p>当云平台发 SIGTERM 触发 Spring 关闭流程时，{@code ContextClosedEvent} 在
 * Tomcat graceful shutdown 之前发布。本监听器在此完成下线编排（标记 health DOWN →
 * 反注册 → 等待消费者刷新缓存），跑完后 Spring 才继续关闭、Tomcat 才开始处理在途请求.</p>
 *
 * <p>作为 {@link GracefulShutdownEndpoint}（preStap 主路径）的兜底——若云平台未配 preStop
 * 或 preStap 失败，SIGTERM 来了 listener 还能兜住，不至于裸关闭. 幂等：preStap 调过端点后
 * flag 已 false、已反注册，listener 再跑一遍无副作用.</p>
 *
 * @author frame-me
 */
public class GracefulShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownListener.class);

    private final GracefulShutdownExecutor executor;

    public GracefulShutdownListener(GracefulShutdownExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("收到 ContextClosedEvent，开始优雅下线兜底编排");
        executor.shutdown();
    }
}

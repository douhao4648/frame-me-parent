package com.frame.me.cloud.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

/**
 * 优雅下线编排核心：标记 health DOWN → 反注册 → 等待消费者刷新缓存.
 *
 * <p>被 {@link GracefulShutdownEndpoint}（preStap 主动触发，主路径）与
 * {@link GracefulShutdownListener}（ContextClosedEvent 兜底）共用，编排逻辑单点维护.</p>
 *
 * <p>幂等：{@link ShutdownReadyFlag#markShuttingDown()} 多次调用无副作用；
 * 反注册对 Nacos 等注册中心重复调用 deregister 也幂等（实例已下线再调返回成功）.
 * 故 preStap 跑一遍、SIGTERM 来 listener 再跑一遍，无副作用.</p>
 *
 * @author frame-me
 */
public class GracefulShutdownExecutor {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownExecutor.class);

    private final ShutdownReadyFlag flag;
    private final GracefulShutdownProperties properties;
    private final ObjectProvider<ServiceRegistry<Registration>> serviceRegistryProvider;
    private final ObjectProvider<Registration> registrationProvider;

    public GracefulShutdownExecutor(ShutdownReadyFlag flag,
                                    GracefulShutdownProperties properties,
                                    ObjectProvider<ServiceRegistry<Registration>> serviceRegistryProvider,
                                    ObjectProvider<Registration> registrationProvider) {
        this.flag = flag;
        this.properties = properties;
        this.serviceRegistryProvider = serviceRegistryProvider;
        this.registrationProvider = registrationProvider;
    }

    /**
     * 执行下线编排：标记 DOWN → 反注册 → 等待.
     * 幂等，可安全重复调用.
     *
     * <p>等待以「实际完成反注册」为前提：消费者的实例缓存来自注册中心，
     * 无注册中心（测试、本地开发、纯任务服务）时没有消费者缓存可刷新，跳过等待.</p>
     */
    public void shutdown() {
        // ① health 立即 DOWN
        flag.markShuttingDown();
        log.info("优雅下线：health 已标记为 DOWN");

        // ② 反注册（无注册中心时跳过，纯定时任务服务等场景）
        boolean deregistered = deregisterIfPresent();

        // ③ 等待消费者刷新本地缓存（仅在真实反注册后才有意义）
        if (deregistered) {
            waitConsumersRefresh();
        }

        // ④ 返回，Spring 继续 → Tomcat graceful shutdown 处理在途请求
    }

    private boolean deregisterIfPresent() {
        ServiceRegistry<Registration> registry = serviceRegistryProvider.getIfAvailable();
        Registration registration = registrationProvider.getIfAvailable();
        if (registry == null || registration == null) {
            log.info("优雅下线：无 ServiceRegistry/Registration bean（无注册中心或纯任务服务），跳过反注册与等待");
            return false;
        }
        try {
            registry.deregister(registration);
            log.info("优雅下线：已从注册中心反注册 {}", registration);
            return true;
        } catch (Exception e) {
            // 反注册失败不阻断下线流程——Tomcat graceful shutdown 仍会处理在途请求
            // 消费者侧会因 health DOWN + 自然过期停止调用
            // 反注册未成功，注册表仍有本实例，等待无意义，跳过
            log.warn("优雅下线：反注册异常，不阻断后续流程: {}", e.getMessage());
            return false;
        }
    }

    private void waitConsumersRefresh() {
        if (properties.getDeregisterWait().isZero() || properties.getDeregisterWait().isNegative()) {
            return;
        }
        log.info("优雅下线：等待 {} 让消费者刷新缓存", properties.getDeregisterWait());
        try {
            Thread.sleep(properties.getDeregisterWait().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("优雅下线：等待被中断，提前结束");
        }
    }
}

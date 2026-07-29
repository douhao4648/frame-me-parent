package com.frame.me.auth.spi;

import java.util.function.Predicate;

/**
 * 注册中心服务名探针接口.
 *
 * <p>判定认证传播的目标主机是否为注册中心可解析的服务名（如 K8s
 * {@code order.default.svc.cluster.local}）。独立接口而非直接使用
 * {@code Predicate<String>} bean，避免与容器中其他通用 Predicate bean 冲突。</p>
 *
 * <p>默认实现基于 Spring Cloud {@code LoadBalancerClient.choose(host)}；
 * 业务方可注册自定义实现覆盖（如直接查询注册中心 API）。</p>
 *
 * @author frame-me
 */
@FunctionalInterface
public interface IServiceInstanceProbe extends Predicate<String> {
}

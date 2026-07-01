package com.frame.me.base.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花 ID 生成工具类.
 *
 * <p>基于 Hutool 内置雪花实现（{@link cn.hutool.core.lang.Snowflake}）。默认由 Hutool
 * 依据主机 MAC + PID 自动推导 workerId / datacenterId，单机开箱即用，脱离 Spring
 * （如纯单元测试）也可直接调用。
 *
 * <p>分布式多副本场景（如 k8s）为避免 ID 冲突，可显式配置
 * {@code me.snowflake.worker-id} / {@code me.snowflake.datacenter-id}，由
 * {@code SnowflakeAutoConfiguration} 在启动时调用 {@link #configure(long, long)} 覆盖默认实例。
 *
 * <p>适用于实体主键之外的雪花 ID 需求（业务单号、入库前预取 ID、文件名等）；实体主键由各
 * MyBatis starter 的 {@code BaseEntity} 自行生成。
 *
 * <p>典型用法：
 * <pre>
 * long id = SnowflakeUtils.nextId();
 * String idStr = SnowflakeUtils.nextIdStr();
 * </pre>
 */
public class SnowflakeUtils {

    private static volatile Snowflake snowflake = IdUtil.getSnowflake();

    private SnowflakeUtils() {
    }

    /**
     * 生成下一个雪花 ID.
     *
     * @return 长整型 ID
     */
    public static long nextId() {
        return snowflake.nextId();
    }

    /**
     * 生成下一个雪花 ID 字符串.
     *
     * @return ID 字符串
     */
    public static String nextIdStr() {
        return snowflake.nextIdStr();
    }

    /**
     * 使用指定的 workerId / datacenterId 重建雪花实例，供分布式部署时覆盖默认自动推导.
     *
     * @param workerId     工作机器 ID（0~31）
     * @param datacenterId 数据中心 ID（0~31）
     */
    public static void configure(long workerId, long datacenterId) {
        snowflake = IdUtil.getSnowflake(workerId, datacenterId);
    }

}

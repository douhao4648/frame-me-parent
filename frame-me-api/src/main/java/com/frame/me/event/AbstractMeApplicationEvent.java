package com.frame.me.event;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 可桥接的本地事件基类.
 *
 * <p>继承此类的事件可通过事件桥接发布器同时发布到：
 * <ul>
 *   <li>本地 Spring 事件管道（同进程内消费）；</li>
 *   <li>跨服务传输通道（Redis / MQ 等）。</li>
 * </ul>
 *
 * <p>具体事件需实现 {@link #getEventType()} 返回类型标识。</p>
 *
 * @author frame-me
 */
public abstract class AbstractMeApplicationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件源.
     */
    @Getter
    private final Object source;

    /**
     * 事件唯一 ID.
     *
     * <p>发送时缺省生成 UUID，业务可在构造后 {@link #setEventId} 覆盖自定义值。
     * 广播时随 {@code EventBridgeMessage} 传输，接收方重建事件后回填，
     * 使用方自行决定是否用于去重/幂等。</p>
     */
    @Getter
    @Setter
    private String eventId;

    /**
     * 创建事件.
     *
     * @param source 事件源
     */
    public AbstractMeApplicationEvent(Object source) {
        this.source = source;
        this.eventId = UUID.randomUUID().toString();
    }

    /**
     * 事件类型标识，用于传输通道分发.
     *
     * @return 类型字符串
     */
    public abstract String getEventType();

    /**
     * 是否广播到跨服务通道，默认 true.
     *
     * @return true 表示广播
     */
    public boolean isBroadcast() {
        return true;
    }

    /**
     * 目标服务名，默认 {@code null} 表示不限制目标服务（广播）。
     *
     * @return 目标服务名
     */
    public String getTargetService() {
        return null;
    }

    /**
     * 目标标识，默认 {@code null} 表示不限制目标实体/实例/用户。
     *
     * @return 目标标识
     */
    public String getTargetId() {
        return null;
    }

    /**
     * 事件负载，默认返回事件源 {@link #getSource()}。
     *
     * <p>业务事件可覆盖此方法，把需要跨服务传输的数据与事件源解耦。</p>
     *
     * @return 负载对象
     */
    public Object getPayload() {
        return source;
    }
}

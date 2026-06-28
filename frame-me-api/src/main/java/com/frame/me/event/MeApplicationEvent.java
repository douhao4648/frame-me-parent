package com.frame.me.event;

import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 可桥接的本地事件基类.
 *
 * <p>继承此类的事件可通过事件桥接发布器同时发布到：
 * <ul>
 *   <li>本地 Spring {@link ApplicationEvent} 管道（同进程内消费）；</li>
 *   <li>跨服务传输通道（Redis / MQ 等）。</li>
 * </ul>
 *
 * <p>具体事件需实现 {@link #getEventType()} 返回类型标识。</p>
 *
 * @author frame-me
 */
public abstract class MeApplicationEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建事件.
     *
     * @param source 事件源
     */
    public MeApplicationEvent(Object source) {
        super(source);
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
     * 事件负载，默认返回事件源 {@link #getSource()}。
     *
     * <p>业务事件可覆盖此方法，把需要跨服务传输的数据与事件源解耦。</p>
     *
     * @return 负载对象
     */
    public Object getPayload() {
        return getSource();
    }
}

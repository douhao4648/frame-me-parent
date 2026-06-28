package com.frame.me.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许通过 SSE / WebSocket 等实时通道向客户端广播的事件标记.
 *
 * <p>只有标注了该注解的事件类，才会被 {@code SseEventDispatcher} 和
 * {@code WsMvcEventDispatcher} 转发给前端客户端。内部事件（如审计日志）若不想暴露，
 * 不标注此注解即可。</p>
 *
 * @author frame-me
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface EventClientPermit {
}

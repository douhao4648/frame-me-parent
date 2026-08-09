package com.frame.me.sso.event;

import com.frame.me.event.MeApplicationEvent;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 用户登出事件（踢人通知）.
 *
 * <p>SSO 踢人时通过事件桥接发布，先本地发布（同进程监听），
 * 再跨进程广播（Redis pub/sub）供下游订阅.
 * 下游收到后清自己 sa-token session 实现即时踢人.</p>
 *
 * <p>下游订阅 type={@value #EVENT_TYPE}：{@code @Import(UserLogoutEventConfiguration.class)}
 * 注册事件类型后，以 {@code @EventListener} 监听本事件，取 userId 清本地 session.</p>
 *
 * @author frame-me
 */
@Getter
@Setter
public class UserLogoutEvent extends MeApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型标识，下游订阅此 type. */
    public static final String EVENT_TYPE = "sso:user-logout";

    private Long userId;
    private String appId;
    private LocalDateTime logoutTime;
    private String reason;

    public UserLogoutEvent() {
        super(null);
    }

    public UserLogoutEvent(Object source, Long userId, String appId, String reason) {
        super(source);
        this.userId = userId;
        this.appId = appId;
        this.logoutTime = LocalDateTime.now();
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    /**
     * 跨进程传输的负载：userId/appId/reason/logoutTime.
     *
     * <p>不复用 source（source 可能是不可序列化的对象），显式返回业务字段.</p>
     */
    @Override
    public Object getPayload() {
        return new Payload(userId, appId, logoutTime, reason);
    }

    /**
     * 跨进程传输负载.
     */
    @Getter
    @Setter
    public static class Payload {
        private Long userId;
        private String appId;
        private LocalDateTime logoutTime;
        private String reason;

        public Payload() {
        }

        public Payload(Long userId, String appId, LocalDateTime logoutTime, String reason) {
            this.userId = userId;
            this.appId = appId;
            this.logoutTime = logoutTime;
            this.reason = reason;
        }
    }
}

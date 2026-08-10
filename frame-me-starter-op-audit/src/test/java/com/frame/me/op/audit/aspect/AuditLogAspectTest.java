package com.frame.me.op.audit.aspect;

import com.frame.me.op.audit.annotation.AuditLog;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import com.frame.me.op.audit.spi.IAuditLogOperatorSupplier;
import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.base.event.EventBridgePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuditLogAspect} 单元测试.
 *
 * @author frame-me
 */
class AuditLogAspectTest {

    private EventBridgePublisher publisher;
    private ApplicationEventPublisher localPublisher;
    private ObjectProvider<EventBridgePublisher> bridgePublisherProvider;
    private IAuditLogOperatorSupplier operatorSupplier;
    private AuditProperties properties;
    private EventBridgeProperties eventBridgeProperties;
    private AuditService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        publisher = mock(EventBridgePublisher.class);
        localPublisher = mock(ApplicationEventPublisher.class);
        bridgePublisherProvider = mock(ObjectProvider.class);
        operatorSupplier = mock(IAuditLogOperatorSupplier.class);
        properties = new AuditProperties();
        // 默认走桥接路径（定向发送），验证远程闸门的用例自行覆盖此配置
        properties.setTargetService("audit-center");
        eventBridgeProperties = new EventBridgeProperties();
        eventBridgeProperties.setServiceName("test-service");
        when(operatorSupplier.getOperatorId()).thenReturn("operator-1");
        when(bridgePublisherProvider.getIfAvailable()).thenReturn(publisher);

        AuditLogAspect aspect = new AuditLogAspect(localPublisher, bridgePublisherProvider,
                operatorSupplier, properties, eventBridgeProperties);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditService());
        factory.addAspect(aspect);
        service = factory.getProxy();
    }

    /**
     * EventBridge 关闭（无 EventBridgePublisher bean）时降级为仅本地发布，
     * 本进程 @EventListener 照常消费.
     */
    @Test
    void shouldPublishLocallyWhenBridgeAbsent() {
        when(bridgePublisherProvider.getIfAvailable()).thenReturn(null);

        service.simpleAction();

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(localPublisher).publishEvent(captor.capture());
        verify(publisher, times(0)).publish(any());
        assertThat(captor.getValue().getRecord().getAction())
                .isEqualTo(AuditService.class.getName() + "#simpleAction");
    }

    /**
     * 未配 target-service 且 broadcast=false（生产默认）：桥接在场也仅本地发布，
     * 不向 Redis 空发消息.
     */
    @Test
    void shouldPublishLocallyOnlyWhenNoRemoteTarget() {
        properties.setTargetService("");

        service.simpleAction();

        verify(localPublisher).publishEvent(any(AuditLogEvent.class));
        verify(publisher, times(0)).publish(any());
    }

    /**
     * broadcast=true 时即使 target-service 为空也经桥接广播（全员送达语义）.
     */
    @Test
    void shouldBroadcastWhenEnabledWithoutTargetService() {
        properties.setTargetService("");
        properties.setBroadcast(true);

        service.simpleAction();

        verify(publisher, times(1)).publish(any());
        verifyNoInteractions(localPublisher);
    }

    @Test
    void shouldRecordSuccessWithParamsAndResult() {
        service.createUser(new User("alice", "13800138000"));

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher, times(1)).publish(captor.capture());

        AuditLogRecord record = captor.getValue().getRecord();
        assertThat(record.getAction()).isEqualTo("创建用户");
        assertThat(record.getCategory()).isEqualTo("用户管理");
        assertThat(record.getOperatorId()).isEqualTo("operator-1");
        assertThat(record.isSuccess()).isTrue();
        assertThat(record.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(record.getParams()).contains("\"username\":\"alice\"");
        assertThat(record.getResult()).contains("\"alice\"");
        assertThat(record.getDescription()).isEqualTo("创建用户 alice，手机号 13800138000");
        assertThat(record.getSourceService()).isEqualTo("test-service");
    }

    @Test
    void shouldRecordDefaultAction() {
        service.simpleAction();

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().getRecord().getAction())
                .isEqualTo(AuditService.class.getName() + "#simpleAction");
    }

    @Test
    void shouldRecordException() {
        assertThatThrownBy(() -> service.failAction())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        AuditLogRecord record = captor.getValue().getRecord();
        assertThat(record.isSuccess()).isFalse();
        assertThat(record.getErrorMsg()).isEqualTo("boom");
    }

    @Test
    void shouldBridgeToTargetService() {
        properties.setTargetService("audit-service");

        service.createUser(new User("bob", "13900139000"));

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        AuditLogEvent event = captor.getValue();
        assertThat(event.getTargetService()).isEqualTo("audit-service");
        assertThat(event.getRecord().getTargetService()).isEqualTo("audit-service");
    }

    @Test
    void shouldKeepPlaceholderWhenSpelFails() {
        service.badDescription(new User("alice", "13800138000"));

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().getRecord().getDescription())
                .isEqualTo("操作 #unknown.name 失败");
    }

    @Test
    void shouldSkipParamsWhenDisabled() {
        service.noParams(new User("alice", "13800138000"));

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().getRecord().getParams()).isNull();
    }

    @Test
    void shouldSkipResultWhenDisabled() {
        service.noResult(new User("alice", "13800138000"));

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().getRecord().getResult()).isNull();
    }

    @Test
    void shouldSkipErrorWhenDisabled() {
        assertThatThrownBy(() -> service.noError())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().getRecord().getErrorMsg()).isNull();
    }

    @Test
    void shouldSerializeErrorForUnserializableObject() {
        service.badParam(new BadObject());

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        // IgnoreErrorGetter：坏 getter 被跳过而非整体失败，序列化返回空对象 JSON（而非 [serialize-error]）
        // 循环引用 / 栈溢出等极端场景才降级为占位符
        assertThat(captor.getValue().getRecord().getParams()).contains("{}");
    }

    @Test
    void shouldTruncateParamsByByteLength() {
        properties.setMaxParamLength(10);

        service.createUser(new User("中文用户名", "13800138000"));

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        String params = captor.getValue().getRecord().getParams();
        assertThat(params.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(13); // 10 bytes + "..." (3 bytes)
    }

    /**
     * 操作人 SPI 抛异常时降级为 anonymous，不阻断被审计的业务方法.
     */
    @Test
    void shouldFallbackOperatorWhenSupplierThrows() {
        when(operatorSupplier.getOperatorId()).thenThrow(new IllegalStateException("SPI down"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.simpleAction());

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getRecord().getOperatorId()).isEqualTo("anonymous");
    }

    /**
     * 返回值序列化也受 maxParamLength 截断，与 params 行为一致.
     */
    @Test
    void shouldTruncateResultByByteLength() {
        properties.setMaxParamLength(10);

        service.bigResult();

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(publisher).publish(captor.capture());

        String result = captor.getValue().getRecord().getResult();
        assertThat(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(13); // 10 bytes + "..." (3 bytes)
    }

    public static class AuditService {

        @AuditLog(action = "创建用户", category = "用户管理",
                description = "创建用户 #user.username，手机号 #user.phone")
        public User createUser(User user) {
            return user;
        }

        @AuditLog(description = "操作 #unknown.name 失败")
        public User badDescription(User user) {
            return user;
        }

        @AuditLog(recordParams = false)
        public User noParams(User user) {
            return user;
        }

        @AuditLog(recordResult = false)
        public User noResult(User user) {
            return user;
        }

        @AuditLog(recordError = false)
        public void noError() {
            throw new IllegalStateException("boom");
        }

        @AuditLog
        public void badParam(BadObject bad) {
        }

        @AuditLog
        public void simpleAction() {
        }

        @AuditLog
        public void failAction() {
            throw new IllegalStateException("boom");
        }

        @AuditLog(recordParams = false)
        public String bigResult() {
            return "x".repeat(200);
        }
    }

    public record User(String username, String phone) {
    }

    public static class BadObject {
        public String getValue() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}

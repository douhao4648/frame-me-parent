package com.frame.me.op.audit.aspect;

import com.frame.me.op.audit.annotation.AuditLog;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import com.frame.me.op.audit.spi.AuditLogOperatorSupplier;
import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.base.event.EventBridgePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuditLogAspect} 单元测试.
 *
 * @author frame-me
 */
class AuditLogAspectTest {

    private EventBridgePublisher publisher;
    private AuditLogOperatorSupplier operatorSupplier;
    private AuditProperties properties;
    private EventBridgeProperties eventBridgeProperties;
    private AuditService service;

    @BeforeEach
    void setUp() {
        publisher = mock(EventBridgePublisher.class);
        operatorSupplier = mock(AuditLogOperatorSupplier.class);
        properties = new AuditProperties();
        eventBridgeProperties = new EventBridgeProperties();
        eventBridgeProperties.setServiceName("test-service");
        when(operatorSupplier.getOperatorId()).thenReturn("operator-1");

        AuditLogAspect aspect = new AuditLogAspect(publisher, operatorSupplier, properties, eventBridgeProperties);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditService());
        factory.addAspect(aspect);
        service = factory.getProxy();
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

        assertThat(captor.getValue().getRecord().getParams()).contains("[serialize-error]");
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
    }

    public record User(String username, String phone) {
    }

    public static class BadObject {
        public String getValue() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}

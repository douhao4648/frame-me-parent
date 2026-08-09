package com.frame.me.op.audit.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditLogLogger} 单元测试.
 *
 * @author frame-me
 */
class AuditLogLoggerTest {

    private static final String SELF_INSTANCE = "self-instance-1";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(AuditLogLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void shouldLogWhenEnabled() {
        AuditLogLogger loggerListener = new AuditLogLogger(enabledProperties(), currentInstance());

        AuditLogRecord record = new AuditLogRecord();
        record.setAction("创建用户");
        record.setSuccess(true);
        record.setTimestamp(Instant.now());

        loggerListener.onAuditLog(new AuditLogEvent("test-service", record, null, SELF_INSTANCE));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("创建用户");
    }

    @Test
    void shouldSkipWhenDisabled() {
        AuditProperties properties = new AuditProperties();
        properties.setLogEnabled(false);
        AuditLogLogger loggerListener = new AuditLogLogger(properties, currentInstance());

        AuditLogRecord record = new AuditLogRecord();
        record.setAction("删除用户");

        loggerListener.onAuditLog(new AuditLogEvent("test-service", record, null, SELF_INSTANCE));

        assertThat(appender.list).isEmpty();
    }

    /**
     * 其他实例广播的审计事件（桥接重发布）不打印，避免同服务多实例重复记录.
     */
    @Test
    void shouldSkipRemoteBroadcastEvent() {
        AuditLogLogger loggerListener = new AuditLogLogger(enabledProperties(), currentInstance());

        AuditLogRecord record = new AuditLogRecord();
        record.setAction("其他实例的操作");

        loggerListener.onAuditLog(new AuditLogEvent("test-service", record, null, "other-instance"));

        assertThat(appender.list).isEmpty();
    }

    private AuditProperties enabledProperties() {
        AuditProperties properties = new AuditProperties();
        properties.setLogEnabled(true);
        return properties;
    }

    private EventBridgeProperties currentInstance() {
        EventBridgeProperties eventBridgeProperties = new EventBridgeProperties();
        eventBridgeProperties.setServiceName("test-service");
        eventBridgeProperties.setInstanceId(SELF_INSTANCE);
        return eventBridgeProperties;
    }
}

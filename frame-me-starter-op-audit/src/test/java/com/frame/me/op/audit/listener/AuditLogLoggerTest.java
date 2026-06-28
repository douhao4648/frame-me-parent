package com.frame.me.op.audit.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
        AuditProperties properties = new AuditProperties();
        properties.setLogEnabled(true);
        AuditLogLogger loggerListener = new AuditLogLogger(properties);

        AuditLogRecord record = new AuditLogRecord();
        record.setAction("创建用户");
        record.setSuccess(true);
        record.setTimestamp(Instant.now());

        loggerListener.onAuditLog(new AuditLogEvent("test-service", record, null));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("创建用户");
    }

    @Test
    void shouldSkipWhenDisabled() {
        AuditProperties properties = new AuditProperties();
        properties.setLogEnabled(false);
        AuditLogLogger loggerListener = new AuditLogLogger(properties);

        AuditLogRecord record = new AuditLogRecord();
        record.setAction("删除用户");

        loggerListener.onAuditLog(new AuditLogEvent("test-service", record, null));

        assertThat(appender.list).isEmpty();
    }
}

package com.frame.me.audit.mapper;

import com.frame.me.audit.entity.LogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * audit_log 真实 schema 边界测试（test profile 以 H2 MySQL 模式执行 schema.sql）.
 *
 * <p>契约：默认 action 为全限定类名#方法名（常态 70~150 字符），action 列必须完整
 * 存放——VARCHAR(64) 时代严格 MySQL 插入失败丢审计、非严格模式截断致不同 action
 * 碰撞，现为 VARCHAR(255)。本测试用 >64 字符的默认风格 action 钉住该契约。</p>
 *
 * @author frame-me
 */
@SpringBootTest
@ActiveProfiles("test")
class LogMapperSchemaTest {

    @Autowired
    private LogMapper logMapper;

    /**
     * 超过 64 字符的默认风格 action：完整入库、读回无截断.
     */
    @Test
    void longDefaultAction_persistsWithoutTruncation() {
        String longAction = "com.frame.me.audit.service.impl.LongNamedBusinessService#updateSomeBusinessEntity";
        assertThat(longAction.length()).isGreaterThan(64);

        LogEntity entity = new LogEntity();
        entity.setAction(longAction);
        entity.setCategory("AUTH");
        entity.setSuccess(true);
        entity.setTimestamp(LocalDateTime.now());
        logMapper.insert(entity);

        LogEntity loaded = logMapper.selectOneById(entity.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAction()).isEqualTo(longAction);
    }
}

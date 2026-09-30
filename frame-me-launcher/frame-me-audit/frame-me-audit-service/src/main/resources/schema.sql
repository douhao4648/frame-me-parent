-- 审计日志表
-- event_id 为事件幂等标识，唯一约束兜底去重（MySQL/H2 均允许多个 NULL：旧链路无 eventId 不冲突）。
-- 存量表需手工迁移：ALTER TABLE audit_log ADD COLUMN event_id VARCHAR(64) UNIQUE;
-- action 须容纳默认 action（全限定类名#方法名，常态 70~150 字符）：VARCHAR(64) 时代严格 MySQL
-- 插入失败丢审计、非严格模式截断致不同 action 碰撞，故定为 VARCHAR(255)。
-- 存量表需手工迁移：ALTER TABLE audit_log MODIFY COLUMN action VARCHAR(255);
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL PRIMARY KEY,
    event_id VARCHAR(64) UNIQUE,
    action VARCHAR(255),
    category VARCHAR(64),
    description TEXT,
    operator_id VARCHAR(64),
    target_id VARCHAR(64),
    params TEXT,
    result TEXT,
    success TINYINT(1),
    error_msg TEXT,
    duration_ms BIGINT,
    timestamp DATETIME(3),
    source_service VARCHAR(64),
    target_service VARCHAR(64),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0
);

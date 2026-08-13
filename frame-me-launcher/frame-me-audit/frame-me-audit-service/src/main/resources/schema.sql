-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL PRIMARY KEY,
    action VARCHAR(64),
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

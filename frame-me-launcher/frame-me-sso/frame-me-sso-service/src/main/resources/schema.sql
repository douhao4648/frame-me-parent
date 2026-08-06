-- SSO 应用注册表
CREATE TABLE IF NOT EXISTS sso_app (
    id BIGINT NOT NULL PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    access_type VARCHAR(16) NOT NULL,
    app_secret VARCHAR(256),
    redirect_uris TEXT,
    scopes VARCHAR(256),
    status VARCHAR(16) NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_app_id (app_id)
);

-- SSO 用户
CREATE TABLE IF NOT EXISTS sso_user (
    id BIGINT NOT NULL PRIMARY KEY,
    account VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    name VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    roles VARCHAR(256),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_account (account)
);

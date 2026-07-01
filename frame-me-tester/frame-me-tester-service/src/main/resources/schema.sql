CREATE TABLE IF NOT EXISTS demo_user (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    age INT,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    version INT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS flex_demo (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    age INT,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    version INT DEFAULT 1
);

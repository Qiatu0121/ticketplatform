-- 校园活动抢票平台数据库初始化脚本
CREATE DATABASE IF NOT EXISTS `ticket_platform`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE `ticket_platform`;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    nickname VARCHAR(50),
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `activity` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    start_time DATETIME,
    location VARCHAR(200),
    detail TEXT,
    status TINYINT DEFAULT 0 COMMENT '0 未开始，1 进行中，2 已结束',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `activity_ticket` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    name VARCHAR(50),
    price DECIMAL(10, 2) DEFAULT 0,
    total_stock INT DEFAULT 0,
    stock INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ticket_activity (activity_id)
);

CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    request_id VARCHAR(64) NULL COMMENT '异步下单请求 ID',
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    `count` INT DEFAULT 1,
    amount DECIMAL(10, 2),
    status TINYINT DEFAULT 0 COMMENT '0 待支付，1 已支付，2 已取消，3 已核销，4 已退款',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    pay_time DATETIME,
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    UNIQUE KEY uk_order_request (request_id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_status_create (status, create_time)
);

CREATE TABLE IF NOT EXISTS `order_log` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_log_order (order_id)
);

-- 异步下单请求表，同时作为事务性 Outbox
CREATE TABLE IF NOT EXISTS `grab_request` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL COMMENT '客户端生成的幂等键',
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    `count` INT NOT NULL DEFAULT 1,
    amount DECIMAL(10, 2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待处理，1 成功，2 失败',
    order_id BIGINT NULL,
    publish_state TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发布，1 发布中，2 已确认，3 等待重发',
    publish_attempts INT NOT NULL DEFAULT 0,
    last_publish_time DATETIME NULL,
    next_publish_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consume_attempts INT NOT NULL DEFAULT 0,
    redis_token VARCHAR(96) NOT NULL COMMENT 'Redis 回补幂等标记',
    redis_rollback_state TINYINT NOT NULL DEFAULT 0 COMMENT '0 无需回补，1 待回补，2 已回补',
    expire_time DATETIME NOT NULL,
    error_message VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_grab_request_id (request_id),
    KEY idx_grab_request_status_expire (status, expire_time),
    KEY idx_grab_request_publish (status, next_publish_time),
    KEY idx_grab_request_user_activity (user_id, activity_id, status)
);

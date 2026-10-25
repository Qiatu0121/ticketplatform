-- 已有数据库升级脚本：RabbitMQ 异步下单
-- 执行前确认 order_no 没有重复值：
-- SELECT order_no, COUNT(*) AS c FROM `order` GROUP BY order_no HAVING c > 1;
USE ticket_platform;

ALTER TABLE `order`
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '异步下单请求 ID' AFTER order_no;

ALTER TABLE `order`
    ADD UNIQUE KEY uk_order_request (request_id),
    ADD UNIQUE KEY uk_order_no (order_no);

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

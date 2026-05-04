-- 操作日志审计表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL,
    `username` VARCHAR(64) DEFAULT NULL,
    `operation` VARCHAR(200) NOT NULL,
    `module` VARCHAR(64) DEFAULT NULL,
    `type` VARCHAR(20) DEFAULT NULL,
    `method` VARCHAR(255) DEFAULT NULL,
    `request_url` VARCHAR(255) DEFAULT NULL,
    `request_method` VARCHAR(10) DEFAULT NULL,
    `request_params` TEXT DEFAULT NULL,
    `response_result` TEXT DEFAULT NULL,
    `ip` VARCHAR(64) DEFAULT NULL,
    `duration` BIGINT DEFAULT NULL,
    `status` TINYINT DEFAULT 1,
    `error_msg` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_oplog_user` (`user_id`),
    INDEX `idx_oplog_module` (`module`),
    INDEX `idx_oplog_type` (`type`),
    INDEX `idx_oplog_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志审计表';

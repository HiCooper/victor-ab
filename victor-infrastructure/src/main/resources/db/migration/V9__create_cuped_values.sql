CREATE TABLE IF NOT EXISTS `victor_cuped_values` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `exp_id` VARCHAR(64) NOT NULL COMMENT '实验ID',
    `report_date` DATE NOT NULL COMMENT '计算日期',
    `variant` VARCHAR(64) NOT NULL COMMENT '变体标识',
    `cuped_adjusted_mean` DOUBLE DEFAULT NULL COMMENT 'CUPED调整后均值',
    `cuped_adjusted_variance` DOUBLE DEFAULT NULL COMMENT 'CUPED调整后方差',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_exp_variant_date` (`exp_id`, `variant`, `report_date`),
    INDEX `idx_exp_id` (`exp_id`),
    INDEX `idx_exp_date` (`exp_id`, `report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='CUPED方差缩减值表 - 由StatsDailyJob定时写入，API实时读取补充';

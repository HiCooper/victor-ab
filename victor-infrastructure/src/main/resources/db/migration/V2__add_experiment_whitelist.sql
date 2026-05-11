-- 实验白名单表
-- 用于强制指定用户进入特定分桶，跳过常规 hash 分桶逻辑
CREATE TABLE IF NOT EXISTS `victor_experiment_whitelist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exp_id` VARCHAR(64) NOT NULL COMMENT '实验业务ID',
    `bucket_id` VARCHAR(64) NOT NULL COMMENT '分桶ID',
    `user_ids` TEXT NOT NULL COMMENT '用户ID列表，逗号分隔',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_exp_id` (`exp_id`),
    INDEX `idx_exp_bucket` (`exp_id`, `bucket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验白名单表';

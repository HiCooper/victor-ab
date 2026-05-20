-- ============================================
-- V3: Experiment Report Storage
-- Stores pre-computed statistical analysis results
-- ============================================

CREATE TABLE IF NOT EXISTS `victor_experiment_report` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `exp_id` VARCHAR(64) NOT NULL COMMENT '实验ID',
    `report_date` DATE NOT NULL COMMENT '报告日期',

    -- SRM check results
    `srm_passed` TINYINT(1) DEFAULT 0,
    `srm_p_value` DOUBLE DEFAULT NULL,
    `srm_chi_square` DOUBLE DEFAULT NULL,
    `srm_message` VARCHAR(255) DEFAULT NULL,

    -- Primary metric results
    `primary_metric_name` VARCHAR(64) DEFAULT 'conversion_rate',
    `primary_p_value` DOUBLE DEFAULT NULL,
    `primary_lift` DOUBLE DEFAULT NULL,
    `primary_lift_ci_lower` DOUBLE DEFAULT NULL,
    `primary_lift_ci_upper` DOUBLE DEFAULT NULL,
    `primary_significant` TINYINT(1) DEFAULT 0,

    -- CUPED
    `cuped_applied` TINYINT(1) DEFAULT 0 COMMENT '是否应用了CUPED方差缩减',

    -- Secondary metrics (JSON array of TestResult)
    `secondary_results_json` JSON DEFAULT NULL,

    -- Guardrail metrics (JSON array of SequentialTestResult)
    `guardrail_results_json` JSON DEFAULT NULL,

    -- Variant summaries (JSON map of variant -> summary)
    `variant_summaries_json` JSON DEFAULT NULL,

    -- Daily trends (JSON map of variant -> daily metrics array)
    `daily_trends_json` JSON DEFAULT NULL,

    -- Recommendation
    `recommendation` VARCHAR(32) DEFAULT NULL COMMENT 'LAUNCH/DO_NOT_LAUNCH/CONTINUE_EXPERIMENT/INCONCLUSIVE',
    `recommendation_reason` VARCHAR(512) DEFAULT NULL,

    -- Metadata
    `generated_at` BIGINT DEFAULT 0 COMMENT '生成耗时(ms)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX `idx_exp_id` (`exp_id`),
    INDEX `idx_report_date` (`report_date`),
    UNIQUE KEY `uk_exp_report_date` (`exp_id`, `report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验统计报告';

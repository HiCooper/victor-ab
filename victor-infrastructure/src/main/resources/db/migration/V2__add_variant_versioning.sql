-- Victor AB Experiment System Database Schema Migration
-- Version: V2__add_variant_versioning.sql
-- Description: Add version control to victor_variant table

-- ============================================
-- 为分桶表添加版本控制
-- ============================================

-- 添加版本字段
ALTER TABLE victor_variant 
ADD COLUMN version VARCHAR(32) NOT NULL DEFAULT '00000000000000' COMMENT '版本号: 时间戳格式 20260506143000' AFTER exp_id;

-- 添加活跃版本标记
ALTER TABLE victor_variant 
ADD COLUMN is_active BOOLEAN DEFAULT TRUE COMMENT '当前活跃版本' AFTER params;

-- 更新现有数据
UPDATE victor_variant 
SET version = DATE_FORMAT(created_at, '%Y%m%d%H%i%s'),
    is_active = TRUE
WHERE version = '00000000000000';

-- 删除旧的唯一索引（先检查是否存在）
SET @exists = (SELECT COUNT(*) FROM information_schema.statistics 
               WHERE table_schema = 'victor_experiment' 
               AND table_name = 'victor_variant' 
               AND index_name = 'uk_exp_variant');

SET @sql = IF(@exists > 0,
    'ALTER TABLE victor_variant DROP INDEX uk_exp_variant',
    'SELECT "Index uk_exp_variant does not exist, skipping" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加新的复合唯一索引 (实验 + 版本 + 分桶)
ALTER TABLE victor_variant 
ADD UNIQUE KEY uk_exp_version_variant (exp_id, version, variant_key);

-- 添加版本查询索引
ALTER TABLE victor_variant 
ADD INDEX idx_exp_version (exp_id, version);

-- 添加活跃版本查询索引
ALTER TABLE victor_variant 
ADD INDEX idx_exp_active (exp_id, is_active);

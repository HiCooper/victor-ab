-- ============================================
-- V4: 实验增加桶范围字段，支持同层多实验
-- 实验的 bucket_start/bucket_end 定义实验在层内占用的桶范围
-- 实验内的变体(victor_bucket)桶范围不能超出实验桶范围
-- 同层内不同实验的桶范围不能重叠
-- ============================================

ALTER TABLE victor_experiment
    ADD COLUMN bucket_start INT DEFAULT 0 COMMENT '实验在层内的起始桶号',
    ADD COLUMN bucket_end INT DEFAULT 9999 COMMENT '实验在层内的结束桶号';

-- 更新现有实验：如果已有变体，从变体推导桶范围；否则使用默认 0-9999
UPDATE victor_experiment e
    LEFT JOIN (
        SELECT exp_id,
               MIN(bucket_start) AS min_start,
               MAX(bucket_end)   AS max_end
        FROM victor_bucket
        WHERE is_active = TRUE
        GROUP BY exp_id
    ) b ON e.exp_id = b.exp_id
SET e.bucket_start = COALESCE(b.min_start, 0),
    e.bucket_end   = COALESCE(b.max_end, 9999)
WHERE b.exp_id IS NOT NULL;

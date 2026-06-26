-- 层内互斥：为实验增加「层内桶段」字段。
-- 每层桶空间为 [0, 9999]（共 10000 桶）。同一层内的多个运行中实验各自占用一段
-- 互不重叠的子区间，从而保证一个用户在一层内最多命中一个实验（层内互斥）；
-- 不同层使用不同 salt 做独立随机化（层间正交）。
--
-- 存量实验默认占满整层 [0, 9999]，保持「一层一实验」的原有行为向后兼容。

ALTER TABLE victor_experiment
    ADD COLUMN bucket_start INT NOT NULL DEFAULT 0 COMMENT '实验在所属层内的桶起始位置(层内互斥)' AFTER layer_id,
    ADD COLUMN bucket_end   INT NOT NULL DEFAULT 9999 COMMENT '实验在所属层内的桶结束位置(层内互斥)' AFTER bucket_start;

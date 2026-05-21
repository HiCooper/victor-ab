-- Fix variant.exp_id: BIGINT → VARCHAR(64) to store business exp_id strings
ALTER TABLE victor_variant DROP FOREIGN KEY victor_variant_ibfk_1;
ALTER TABLE victor_variant MODIFY COLUMN exp_id VARCHAR(64) NOT NULL COMMENT '实验业务ID';

-- Create missing experiment_approval table
CREATE TABLE IF NOT EXISTS victor_experiment_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验业务ID',
    approver VARCHAR(64) NOT NULL COMMENT '审批人',
    action VARCHAR(32) NOT NULL COMMENT '审批动作: approve/reject',
    comment TEXT COMMENT '审批意见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exp_id (exp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验审批记录表';

ALTER TABLE victor_layer ADD COLUMN IF NOT EXISTS enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用';

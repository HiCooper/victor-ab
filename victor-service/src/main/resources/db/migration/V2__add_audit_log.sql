CREATE TABLE IF NOT EXISTS victor_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验业务ID',
    action VARCHAR(32) NOT NULL COMMENT '操作类型: CREATE/START/STOP/APPROVE/REJECT/ARCHIVE',
    operator VARCHAR(64) COMMENT '操作人',
    from_status VARCHAR(32) COMMENT '变更前状态',
    to_status VARCHAR(32) COMMENT '变更后状态',
    reason VARCHAR(512) COMMENT '操作原因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exp_id (exp_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验操作审计日志';

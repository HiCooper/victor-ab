-- ============================================
-- Victor AB Experiment System - Complete Schema
-- Merged from V1-V5 migrations
-- ============================================

-- ============================================
-- 1. 域配置表 (流量分域)
-- ============================================
CREATE TABLE IF NOT EXISTS victor_domain (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    domain_id VARCHAR(64) NOT NULL UNIQUE COMMENT '业务ID，用于API',
    name VARCHAR(128) COMMENT '名称',
    traffic_ratio DECIMAL(5,4) DEFAULT 1.0 COMMENT '流量占比',
    is_exclusive BOOLEAN DEFAULT FALSE COMMENT '是否独占域',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_domain_id (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域配置表';

-- ============================================
-- 2. 层配置表 (正交分层)
-- ============================================
CREATE TABLE IF NOT EXISTS victor_layer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    layer_id VARCHAR(64) NOT NULL COMMENT '业务ID',
    domain_id BIGINT NOT NULL COMMENT '引用victor_domain.id主键',
    name VARCHAR(128) COMMENT '层名称',
    salt VARCHAR(64) COMMENT '盐值(用于层间正交)',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (domain_id) REFERENCES victor_domain(id),
    UNIQUE KEY uk_layer_domain (layer_id, domain_id),
    INDEX idx_layer_id (layer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='层配置表';

-- ============================================
-- 3. 实验配置表
-- ============================================
CREATE TABLE IF NOT EXISTS victor_experiment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exp_id VARCHAR(64) NOT NULL UNIQUE COMMENT '业务ID',
    name VARCHAR(128) NOT NULL COMMENT '实验名称',
    description TEXT COMMENT '实验描述',
    layer_id BIGINT NOT NULL COMMENT '引用victor_layer.id主键',
    status ENUM(
        'draft', 'pending_approval', 'approved', 'rejected',
        'review', 'ramp', 'running', 'paused', 'stopped',
        'analyzing', 'decision', 'archive'
    ) DEFAULT 'draft' NOT NULL COMMENT '实验状态',
    targeting_rules JSON COMMENT '定向规则',
    primary_metric VARCHAR(64) COMMENT '主指标',
    secondary_metrics JSON COMMENT '次指标列表',
    guardrail_metrics JSON COMMENT '护栏指标列表',
    auto_ramp_enabled TINYINT(1) DEFAULT 0 COMMENT '是否启用自动灰度推进',
    ramp_config JSON DEFAULT NULL COMMENT '灰度推进配置: {"stages":{"STAGE_1":2,"STAGE_5":4,...}}',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    created_by VARCHAR(64) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (layer_id) REFERENCES victor_layer(id),
    INDEX idx_exp_id (exp_id),
    INDEX idx_status (status),
    INDEX idx_layer (layer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验配置表';

-- ============================================
-- 4. 版本配置表 (唯一版本数据源)
-- ============================================
CREATE TABLE IF NOT EXISTS victor_bucket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验业务ID (victor_experiment.exp_id)',
    version VARCHAR(32) NOT NULL DEFAULT '00000000000000' COMMENT '版本号: 时间戳格式 20260506143000',
    bucket_key VARCHAR(64) NOT NULL COMMENT '版本标识',
    name VARCHAR(128) COMMENT '版本名称',
    bucket_start INT COMMENT '桶起始位置',
    bucket_end INT COMMENT '桶结束位置',
    params TEXT COMMENT '版本参数',
    is_active BOOLEAN DEFAULT TRUE COMMENT '当前活跃版本',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exp_version_bucket (exp_id, version, bucket_key),
    INDEX idx_exp_id (exp_id),
    INDEX idx_exp_version (exp_id, version),
    INDEX idx_exp_active (exp_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本配置表';

-- ============================================
-- 5. 配置版本表 (配置变更追踪)
-- ============================================
CREATE TABLE IF NOT EXISTS victor_config_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(32) NOT NULL UNIQUE COMMENT '版本号',
    etag VARCHAR(16) NOT NULL COMMENT '配置摘要',
    config_json LONGTEXT COMMENT '完整配置JSON快照',
    change_type ENUM('full', 'incremental') DEFAULT 'full' COMMENT '变更类型',
    changed_experiments JSON COMMENT '变更的实验ID列表',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置版本表';

-- ============================================
-- 6. 实验白名单表
-- ============================================
CREATE TABLE IF NOT EXISTS victor_experiment_whitelist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验业务ID',
    bucket_id VARCHAR(64) NOT NULL COMMENT '分桶ID',
    user_ids TEXT NOT NULL COMMENT '用户ID列表，逗号分隔',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exp_id (exp_id),
    INDEX idx_exp_bucket (exp_id, bucket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验白名单表';

-- ============================================
-- 7. 实验审批记录表
-- ============================================
CREATE TABLE IF NOT EXISTS victor_experiment_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验业务ID',
    approver_id BIGINT COMMENT '审批人ID',
    approver_name VARCHAR(64) COMMENT '审批人名称',
    status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '审批状态: pending/approved/rejected',
    comment TEXT COMMENT '审批意见',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exp_id (exp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验审批记录表';

-- ============================================
-- 8. 实验统计报告表
-- ============================================
CREATE TABLE IF NOT EXISTS victor_experiment_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验ID',
    report_date DATE NOT NULL COMMENT '报告日期',
    srm_passed TINYINT(1) DEFAULT 0 COMMENT 'SRM检验是否通过',
    srm_p_value DOUBLE DEFAULT NULL COMMENT 'SRM p值',
    srm_chi_square DOUBLE DEFAULT NULL COMMENT 'SRM卡方值',
    srm_message VARCHAR(255) DEFAULT NULL COMMENT 'SRM消息',
    primary_metric_name VARCHAR(64) DEFAULT 'conversion_rate' COMMENT '主指标名称',
    primary_p_value DOUBLE DEFAULT NULL COMMENT '主指标p值',
    primary_lift DOUBLE DEFAULT NULL COMMENT '主指标提升',
    primary_lift_ci_lower DOUBLE DEFAULT NULL COMMENT '主指标置信区间下界',
    primary_lift_ci_upper DOUBLE DEFAULT NULL COMMENT '主指标置信区间上界',
    primary_significant TINYINT(1) DEFAULT 0 COMMENT '主指标是否显著',
    cuped_applied TINYINT(1) DEFAULT 0 COMMENT '是否应用CUPED方差缩减',
    secondary_results_json JSON DEFAULT NULL COMMENT '次指标结果JSON',
    guardrail_results_json JSON DEFAULT NULL COMMENT '护栏指标结果JSON',
    bucket_summaries_json JSON DEFAULT NULL COMMENT '分桶摘要JSON',
    daily_trends_json JSON DEFAULT NULL COMMENT '每日趋势JSON',
    recommendation VARCHAR(32) DEFAULT NULL COMMENT '建议: LAUNCH/DO_NOT_LAUNCH/CONTINUE_EXPERIMENT/INCONCLUSIVE',
    recommendation_reason VARCHAR(512) DEFAULT NULL COMMENT '建议理由',
    generated_at BIGINT DEFAULT 0 COMMENT '生成耗时(ms)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exp_id (exp_id),
    INDEX idx_report_date (report_date),
    UNIQUE KEY uk_exp_report_date (exp_id, report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验统计报告表';

-- ============================================
-- 9. CUPED方差缩减值表
-- ============================================
CREATE TABLE IF NOT EXISTS victor_cuped_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exp_id VARCHAR(64) NOT NULL COMMENT '实验ID',
    report_date DATE NOT NULL COMMENT '计算日期',
    bucket VARCHAR(64) NOT NULL COMMENT '分桶标识',
    cuped_adjusted_mean DOUBLE DEFAULT NULL COMMENT 'CUPED调整后均值',
    cuped_adjusted_variance DOUBLE DEFAULT NULL COMMENT 'CUPED调整后方差',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exp_bucket_date (exp_id, bucket, report_date),
    INDEX idx_exp_id (exp_id),
    INDEX idx_exp_date (exp_id, report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CUPED方差缩减值表';

-- ============================================
-- 10. 用户表 (JWT认证)
-- ============================================
CREATE TABLE IF NOT EXISTS victor_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    email VARCHAR(128),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 11. RBAC 角色表
-- ============================================
CREATE TABLE IF NOT EXISTS rbac_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '角色名: ADMIN/OPERATOR/VIEWER',
    description VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色表';

-- ============================================
-- 12. RBAC 用户-角色关联表
-- ============================================
CREATE TABLE IF NOT EXISTS rbac_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC用户-角色关联表';

-- ============================================
-- 13. RBAC 角色-权限关联表
-- ============================================
CREATE TABLE IF NOT EXISTS rbac_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission VARCHAR(64) NOT NULL COMMENT '权限枚举名',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色-权限关联表';

-- ============================================
-- 14. 报告任务持久化表
-- ============================================
CREATE TABLE IF NOT EXISTS victor_report_job (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    experiment_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    progress INT DEFAULT 0,
    message VARCHAR(512),
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_experiment_id (experiment_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告任务持久化表';

-- ============================================
-- 初始化数据
-- ============================================

-- 默认域和层
INSERT IGNORE INTO victor_domain (domain_id, name, traffic_ratio, is_exclusive) VALUES
('default', '默认域', 1.0, FALSE);

INSERT IGNORE INTO victor_layer (layer_id, domain_id, name, salt, sort_order) VALUES
('default', 1, '默认层', 'victor_default', 0);

-- 默认角色
INSERT IGNORE INTO rbac_role (id, name, description) VALUES
(1, 'ADMIN', '系统管理员，拥有全部权限'),
(2, 'OPERATOR', '实验运营，可创建/编辑/审批/查看实验和分析'),
(3, 'VIEWER', '只读用户，仅可查看实验和分析'),
(4, 'SDK_CLIENT', 'SDK client for bucketing/config/event APIs');

-- ADMIN 权限 (全部)
INSERT IGNORE INTO rbac_role_permission (role_id, permission) VALUES
(1, 'CREATE_EXPERIMENT'),
(1, 'EDIT_EXPERIMENT'),
(1, 'DELETE_EXPERIMENT'),
(1, 'VIEW_EXPERIMENT'),
(1, 'APPROVE_EXPERIMENT'),
(1, 'SUBMIT_APPROVAL'),
(1, 'VIEW_TRAFFIC'),
(1, 'VIEW_ANALYSIS'),
(1, 'POWER_ANALYSIS'),
(1, 'MANAGE_USERS');

-- OPERATOR 权限 (创建/编辑/审批/查看，无用户管理)
INSERT IGNORE INTO rbac_role_permission (role_id, permission) VALUES
(2, 'CREATE_EXPERIMENT'),
(2, 'EDIT_EXPERIMENT'),
(2, 'VIEW_EXPERIMENT'),
(2, 'APPROVE_EXPERIMENT'),
(2, 'SUBMIT_APPROVAL'),
(2, 'VIEW_TRAFFIC'),
(2, 'VIEW_ANALYSIS'),
(2, 'POWER_ANALYSIS');

-- VIEWER 权限 (只读)
INSERT IGNORE INTO rbac_role_permission (role_id, permission) VALUES
(3, 'VIEW_EXPERIMENT'),
(3, 'VIEW_TRAFFIC'),
(3, 'VIEW_ANALYSIS');

-- 默认管理员用户 (password: admin123, BCrypt hashed)
INSERT IGNORE INTO victor_user (username, password_hash, email, enabled) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@gateflow.local', TRUE);

-- 管理员用户关联 ADMIN 角色
INSERT IGNORE INTO rbac_user_role (user_id, role_id)
SELECT u.id, r.id
FROM victor_user u, rbac_role r
WHERE u.username = 'admin' AND r.name = 'ADMIN';
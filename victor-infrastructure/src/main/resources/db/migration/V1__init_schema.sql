-- Victor AB Experiment System Database Schema
-- Version: V1
-- Description: Initialize Victor database tables

-- ============================================
-- 域配置表 (流量分域)
-- ============================================
CREATE TABLE victor_domain (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    domain_id VARCHAR(64) NOT NULL UNIQUE COMMENT '业务ID，用于API',
    name VARCHAR(128) COMMENT '名称',
    traffic_ratio DECIMAL(5,4) DEFAULT 1.0 COMMENT '流量占比',
    is_exclusive BOOLEAN DEFAULT FALSE COMMENT '是否独占域',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_domain_id (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域配置表';

-- ============================================
-- 层配置表 (正交分层)
-- ============================================
CREATE TABLE victor_layer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    layer_id VARCHAR(64) NOT NULL COMMENT '业务ID',
    domain_id BIGINT NOT NULL COMMENT '引用victor_domain.id主键',
    name VARCHAR(128) COMMENT '层名称',
    salt VARCHAR(64) COMMENT '盐值(用于层间正交)',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (domain_id) REFERENCES victor_domain(id),
    UNIQUE KEY uk_layer_domain (layer_id, domain_id),
    INDEX idx_layer_id (layer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='层配置表';

-- ============================================
-- 实验配置表
-- ============================================
CREATE TABLE victor_experiment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exp_id VARCHAR(64) NOT NULL UNIQUE COMMENT '业务ID',
    name VARCHAR(128) NOT NULL COMMENT '实验名称',
    description TEXT COMMENT '实验描述',
    layer_id BIGINT NOT NULL COMMENT '引用victor_layer.id主键',
    status ENUM('draft', 'review', 'ramp', 'running', 'paused', 'analyzing', 'decision', 'archive') DEFAULT 'draft' COMMENT '实验状态',
    targeting_rules JSON COMMENT '定向规则',
    primary_metric VARCHAR(64) COMMENT '主指标',
    secondary_metrics JSON COMMENT '次指标列表',
    guardrail_metrics JSON COMMENT '护栏指标列表',
    start_time TIMESTAMP COMMENT '开始时间',
    end_time TIMESTAMP COMMENT '结束时间',
    created_by VARCHAR(64) COMMENT '创建人',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (layer_id) REFERENCES victor_layer(id),
    INDEX idx_exp_id (exp_id),
    INDEX idx_status (status),
    INDEX idx_layer (layer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验配置表';

-- ============================================
-- 版本配置表 (唯一版本数据源)
-- ============================================
CREATE TABLE victor_variant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exp_id BIGINT NOT NULL COMMENT '引用victor_experiment.id主键',
    version VARCHAR(32) NOT NULL DEFAULT '00000000000000' COMMENT '版本号: 时间戳格式 20260506143000',
    variant_key VARCHAR(64) NOT NULL COMMENT '版本标识',
    name VARCHAR(128) COMMENT '版本名称',
    bucket_start INT COMMENT '桶起始位置',
    bucket_end INT COMMENT '桶结束位置',
    params TEXT NOT NULL COMMENT '版本参数',
    is_active BOOLEAN DEFAULT TRUE COMMENT '当前活跃版本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exp_id) REFERENCES victor_experiment(id),
    UNIQUE KEY uk_exp_version_variant (exp_id, version, variant_key),
    INDEX idx_exp_id (exp_id),
    INDEX idx_exp_version (exp_id, version),
    INDEX idx_exp_active (exp_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本配置表';

-- ============================================
-- 配置版本表 (配置变更追踪)
-- ============================================
CREATE TABLE victor_config_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(32) NOT NULL UNIQUE COMMENT '版本号',
    etag VARCHAR(16) NOT NULL COMMENT '配置摘要',
    config_json LONGTEXT COMMENT '完整配置JSON快照',
    change_type ENUM('full', 'incremental') DEFAULT 'full' COMMENT '变更类型',
    changed_experiments JSON COMMENT '变更的实验ID列表',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置版本表';

-- ============================================
-- 初始化数据 - 默认域和层
-- ============================================
INSERT INTO victor_domain (domain_id, name, traffic_ratio, is_exclusive) VALUES
('default', '默认域', 1.0, FALSE);

INSERT INTO victor_layer (layer_id, domain_id, name, salt, sort_order) VALUES
('default', 1, '默认层', 'victor_default', 0);
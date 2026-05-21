-- ============================================
-- V4: RBAC Tables
-- Role-based access control for API authorization
-- ============================================

CREATE TABLE IF NOT EXISTS `rbac_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '角色名: ADMIN/OPERATOR/VIEWER',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色表';

CREATE TABLE IF NOT EXISTS `rbac_user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC用户-角色关联表';

CREATE TABLE IF NOT EXISTS `rbac_role_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission` VARCHAR(64) NOT NULL COMMENT '权限枚举名',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色-权限关联表';

-- Seed: default roles
INSERT IGNORE INTO `rbac_role` (`id`, `name`, `description`) VALUES
(1, 'ADMIN', '系统管理员，拥有全部权限'),
(2, 'OPERATOR', '实验运营，可创建/编辑/审批/查看实验和分析'),
(3, 'VIEWER', '只读用户，仅可查看实验和分析');

-- Seed: ADMIN permissions (all)
INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission`) VALUES
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

-- Seed: OPERATOR permissions (create/edit/submit/view, no user management)
INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission`) VALUES
(2, 'CREATE_EXPERIMENT'),
(2, 'EDIT_EXPERIMENT'),
(2, 'VIEW_EXPERIMENT'),
(2, 'APPROVE_EXPERIMENT'),
(2, 'SUBMIT_APPROVAL'),
(2, 'VIEW_TRAFFIC'),
(2, 'VIEW_ANALYSIS'),
(2, 'POWER_ANALYSIS');

-- Seed: VIEWER permissions (read-only)
INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission`) VALUES
(3, 'VIEW_EXPERIMENT'),
(3, 'VIEW_TRAFFIC'),
(3, 'VIEW_ANALYSIS');

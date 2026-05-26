-- V2: User table for JWT authentication
CREATE TABLE IF NOT EXISTS victor_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    email VARCHAR(128),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Default admin user (password: admin123, BCrypt hashed)
INSERT INTO victor_user (username, password_hash, email, enabled)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@gateflow.local', TRUE)
ON DUPLICATE KEY UPDATE username = username;

-- Add SDK_CLIENT role for API key access
INSERT INTO rbac_role (name, description, deleted) VALUES ('SDK_CLIENT', 'SDK client for bucketing/config/event APIs', 0)
ON DUPLICATE KEY UPDATE name = name;

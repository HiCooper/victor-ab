CREATE TABLE IF NOT EXISTS victor_domain (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    domain_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    traffic_ratio DECIMAL(5,4) DEFAULT 1.0,
    is_exclusive BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS victor_layer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    layer_id VARCHAR(64) NOT NULL,
    domain_id BIGINT NOT NULL,
    name VARCHAR(128),
    salt VARCHAR(64),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (domain_id) REFERENCES victor_domain(id),
    UNIQUE KEY uk_layer_domain (layer_id, domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS victor_experiment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exp_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    layer_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    traffic_ratio DECIMAL(5,4),
    bucket_start INT,
    bucket_end INT,
    targeting_rules JSON,
    primary_metric VARCHAR(64),
    secondary_metrics JSON,
    guardrail_metrics JSON,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (layer_id) REFERENCES victor_layer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS victor_variant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exp_id BIGINT NOT NULL,
    variant_key VARCHAR(64) NOT NULL,
    name VARCHAR(128),
    bucket_start INT,
    bucket_end INT,
    params varchar(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exp_id) REFERENCES victor_experiment(id),
    UNIQUE KEY uk_exp_variant (exp_id, variant_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS victor_user_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    exp_id BIGINT NOT NULL,
    variant_key VARCHAR(64) NOT NULL,
    bucket INT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS victor_config_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(32) NOT NULL UNIQUE,
    etag VARCHAR(16) NOT NULL,
    config_json LONGTEXT,
    change_type VARCHAR(20) DEFAULT 'full',
    changed_experiments JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO victor_domain (domain_id, name, traffic_ratio, is_exclusive) VALUES
('default', 'Default Domain', 1.0, FALSE);

INSERT IGNORE INTO victor_layer (layer_id, domain_id, name, salt, sort_order) VALUES
('default', 1, 'Default Layer', 'victor_default', 0);

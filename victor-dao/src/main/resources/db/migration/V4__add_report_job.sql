-- V4: Report job persistence table
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

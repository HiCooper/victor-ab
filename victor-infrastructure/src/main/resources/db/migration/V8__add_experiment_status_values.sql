ALTER TABLE victor_experiment
    MODIFY COLUMN status ENUM(
        'draft', 'pending_approval', 'approved', 'rejected',
        'review', 'ramp', 'running', 'paused', 'stopped',
        'analyzing', 'decision', 'archive'
    ) DEFAULT 'draft' NOT NULL COMMENT '实验状态';

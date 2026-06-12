-- ============================================
-- V3: Add missing indexes for query performance
-- Addresses architecture review findings:
--   bucket_id, approver_id, created_by, email,
--   start_time/end_time
-- ============================================

-- victor_bucket: index on bucket_id (variant identifier) for variant lookups
CREATE INDEX idx_bucket_id ON victor_bucket (bucket_id);

-- victor_experiment_approval: index on approver_id for approval queries
CREATE INDEX idx_approver_id ON victor_experiment_approval (approver_id);

-- victor_experiment: index on created_by for ownership queries
CREATE INDEX idx_created_by ON victor_experiment (created_by);

-- victor_experiment: composite index on start_time/end_time for date range queries
CREATE INDEX idx_exp_time_range ON victor_experiment (start_time, end_time);

-- victor_user: index on email for login/user lookup
CREATE INDEX idx_user_email ON victor_user (email);

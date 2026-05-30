-- V2: Simplify experiment status from 12 to 5 values
-- New lifecycle: draft -> pending_approval -> running -> stopped -> archive

-- Step 1: Migrate existing data to new status values
UPDATE victor_experiment
SET status = 'pending_approval'
WHERE status IN ('approved', 'review');
UPDATE victor_experiment
SET status = 'draft'
WHERE status = 'rejected';
UPDATE victor_experiment
SET status = 'running'
WHERE status = 'ramp';
UPDATE victor_experiment
SET status = 'stopped'
WHERE status IN ('paused', 'analyzing', 'decision');

-- Step 2: Alter ENUM definition
ALTER TABLE victor_experiment
    MODIFY status ENUM('draft','pending_approval','running','stopped','archive')
    DEFAULT 'draft' NOT NULL;

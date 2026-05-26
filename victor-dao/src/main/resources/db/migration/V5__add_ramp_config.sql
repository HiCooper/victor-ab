-- V5: Add ramp_config column to experiment for configurable stage durations
ALTER TABLE victor_experiment
    ADD COLUMN ramp_config JSON DEFAULT NULL COMMENT '灰度推进配置: {"stages":{"STAGE_1":2,"STAGE_5":4,...}}'
    AFTER auto_ramp_enabled;

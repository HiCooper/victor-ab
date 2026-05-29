-- ============================================
-- Real-time Metrics Aggregation Tables
-- Phase 1: ClickHouse Metrics Schema
-- ============================================

-- --------------------------------------------------------
-- Table 1: Experiment Metrics (per minute aggregation)
-- Stores aggregated metrics per experiment/variant/minute
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS victor.experiment_metrics (
    metric_date Date DEFAULT toDate(minute_bucket),
    minute_bucket DateTime,
    exp_id String,
    variant String,
    layer String,
    
    -- Event counts
    total_events UInt64 DEFAULT 0,
    unique_users UInt64 DEFAULT 0,
    
    -- Conversion metrics (for conversion events)
    conversions UInt64 DEFAULT 0,
    conversion_users UInt64 DEFAULT 0,
    
    -- Revenue metrics
    total_revenue Float64 DEFAULT 0,
    avg_revenue Float64 DEFAULT 0,
    
    -- Update timestamp
    updated_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(metric_date)
ORDER BY (metric_date, exp_id, variant, layer, minute_bucket)
SETTINGS index_granularity = 8192;

-- --------------------------------------------------------
-- Table 2: User Statistics (per experiment/variant)
-- Tracks per-user participation and conversions
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS victor.user_experiment_stats (
    stat_date Date DEFAULT toDate(created_at),
    exp_id String,
    variant String,
    layer String,
    user_id String,
    
    -- First seen
    first_seen_at DateTime64(3),
    last_seen_at DateTime64(3),
    
    -- Event counts
    event_count UInt32 DEFAULT 0,
    
    -- Conversion tracking
    converted Bool DEFAULT false,
    first_conversion_at DateTime64(3),
    conversion_count UInt32 DEFAULT 0,
    
    -- Revenue tracking
    total_revenue Float64 DEFAULT 0,
    
    -- Metadata
    platform String DEFAULT '',
    device_id String DEFAULT '',
    created_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(last_seen_at)
PARTITION BY toYYYYMMDD(stat_date)
ORDER BY (stat_date, exp_id, variant, user_id)
SETTINGS index_granularity = 8192;

-- --------------------------------------------------------
-- Table 3: Daily Experiment Summary
-- Daily rollup for fast dashboard queries
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS victor.experiment_daily_summary (
    summary_date Date,
    exp_id String,
    variant String,
    layer String,
    
    -- User counts
    total_users UInt64 DEFAULT 0,
    converted_users UInt64 DEFAULT 0,
    
    -- Event counts
    total_events UInt64 DEFAULT 0,
    
    -- Conversion metrics
    conversion_rate Float64 DEFAULT 0,
    confidence_interval_lower Float64 DEFAULT 0,
    confidence_interval_upper Float64 DEFAULT 0,
    
    -- Revenue
    total_revenue Float64 DEFAULT 0,
    avg_revenue_per_user Float64 DEFAULT 0,
    
    -- Statistical significance (Wilson score lower bound)
    significance_score Float64 DEFAULT 0,
    
    -- Metadata
    updated_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(summary_date)
ORDER BY (summary_date, exp_id, variant, layer)
SETTINGS index_granularity = 8192;

-- --------------------------------------------------------
-- Materialized View: Real-time Event Aggregation
-- Automatically aggregates events into experiment_metrics
-- Uses ARRAY JOIN to expand multi-experiment events into individual rows
-- --------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS victor.mv_experiment_metrics
TO victor.experiment_metrics
AS SELECT
    toStartOfMinute(timestamp) AS minute_bucket,
    exp_id,
    variant,
    layer,
    count() AS total_events,
    uniqExact(user_id) AS unique_users,
    0 AS conversions,
    0 AS conversion_users,
    0.0 AS total_revenue,
    0.0 AS avg_revenue,
    now64(3) AS updated_at
FROM victor.events
ARRAY JOIN
    exp_ids AS exp_id,
    variants AS variant,
    layers AS layer
WHERE length(exp_ids) > 0
GROUP BY
    minute_bucket,
    exp_id,
    variant,
    layer;

-- --------------------------------------------------------
-- Materialized View: Conversion Events Aggregation
-- Tracks conversion events separately
-- Uses ARRAY JOIN to expand multi-experiment events into individual rows
-- --------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS victor.mv_conversion_metrics
TO victor.experiment_metrics
AS SELECT
    toStartOfMinute(timestamp) AS minute_bucket,
    exp_id,
    variant,
    layer,
    count() AS conversions,
    uniqExact(user_id) AS conversion_users,
    sum(toFloat64(JSONExtractString(properties, 'revenue'))) AS total_revenue,
    now64(3) AS updated_at
FROM victor.events
ARRAY JOIN
    exp_ids AS exp_id,
    variants AS variant,
    layers AS layer
WHERE length(exp_ids) > 0
  AND event_type = 'conversion'
GROUP BY
    minute_bucket,
    exp_id,
    variant,
    layer;

-- --------------------------------------------------------
-- View: Combined Real-time Metrics
-- Joins experiment and conversion metrics for dashboard
-- --------------------------------------------------------
CREATE VIEW IF NOT EXISTS victor.v_realtime_metrics
AS
SELECT 
    e.minute_bucket,
    e.exp_id,
    e.variant,
    e.layer,
    e.total_events,
    e.unique_users,
    coalesce(c.conversions, 0) AS conversions,
    coalesce(c.conversion_users, 0) AS conversion_users,
    coalesce(c.total_revenue, 0.0) AS total_revenue,
    CASE 
        WHEN coalesce(c.conversion_users, 0) > 0 
        THEN coalesce(c.total_revenue, 0.0) / c.conversion_users 
        ELSE 0.0 
    END AS avg_revenue,
    e.updated_at
FROM victor.mv_experiment_metrics e
LEFT JOIN victor.mv_conversion_metrics c
    ON e.minute_bucket = c.minute_bucket
    AND e.exp_id = c.exp_id
    AND e.variant = c.variant
    AND e.layer = c.layer;

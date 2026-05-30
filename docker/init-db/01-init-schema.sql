-- ============================================
-- GateFlow ClickHouse Schema
-- AB 实验事件与分析表
-- ============================================

CREATE DATABASE IF NOT EXISTS victor;

-- ============================================
-- 1. 事件原始数据表
-- ============================================
CREATE TABLE IF NOT EXISTS victor.events
(
    event_date  Date DEFAULT toDate(timestamp),
    event_id    String,
    event_type  String,
    user_id     String,
    timestamp   DateTime64(3),
    platform    String,
    device_id   String,
    session_id  String,
    exp_ids     Array(String),
    buckets     Array(String),
    layers      Array(String),
    properties  String,
    received_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(event_date)
ORDER BY (event_date, user_id, timestamp)
SETTINGS index_granularity = 8192;

-- ============================================
-- 2. 实验分流指标聚合表 (SummingMergeTree)
-- ============================================
CREATE TABLE IF NOT EXISTS victor.experiment_metrics
(
    metric_date    Date DEFAULT toDate(minute_bucket),
    minute_bucket  DateTime,
    exp_id         String,
    bucket         String,
    layer          String,
    total_events   UInt64 DEFAULT 0,
    unique_users   UInt64 DEFAULT 0,
    updated_at     DateTime64(3) DEFAULT now64(3)
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(metric_date)
ORDER BY (metric_date, exp_id, bucket, layer, minute_bucket)
SETTINGS index_granularity = 8192;

-- ============================================
-- 3. 用户级实验统计表 (ReplacingMergeTree)
-- ============================================
CREATE TABLE IF NOT EXISTS victor.user_experiment_stats
(
    stat_date           Date DEFAULT toDate(created_at),
    exp_id              String,
    bucket              String,
    layer               String,
    user_id             String,
    first_seen_at       DateTime64(3),
    last_seen_at        DateTime64(3),
    event_count         UInt32 DEFAULT 0,
    converted           Bool DEFAULT false,
    first_conversion_at DateTime64(3),
    conversion_count    UInt32 DEFAULT 0,
    total_revenue       Float64 DEFAULT 0,
    platform            String DEFAULT '',
    device_id           String DEFAULT '',
    created_at          DateTime64(3) DEFAULT now64(3)
) ENGINE = ReplacingMergeTree(last_seen_at)
PARTITION BY toYYYYMMDD(stat_date)
ORDER BY (stat_date, exp_id, bucket, user_id)
SETTINGS index_granularity = 8192;

-- ============================================
-- 4. 实验每日汇总表 (SummingMergeTree)
-- ============================================
CREATE TABLE IF NOT EXISTS victor.experiment_daily_summary
(
    summary_date              Date,
    exp_id                    String,
    bucket                    String,
    layer                     String,
    total_users               UInt64 DEFAULT 0,
    converted_users           UInt64 DEFAULT 0,
    total_events              UInt64 DEFAULT 0,
    conversion_rate           Float64 DEFAULT 0,
    confidence_interval_lower Float64 DEFAULT 0,
    confidence_interval_upper Float64 DEFAULT 0,
    total_revenue             Float64 DEFAULT 0,
    avg_revenue_per_user      Float64 DEFAULT 0,
    significance_score        Float64 DEFAULT 0,
    updated_at                DateTime64(3) DEFAULT now64(3)
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(summary_date)
ORDER BY (summary_date, exp_id, bucket, layer)
SETTINGS index_granularity = 8192;

-- ============================================
-- 5. 物化视图: 分流事件聚合
-- ============================================
CREATE MATERIALIZED VIEW IF NOT EXISTS victor.mv_experiment_metrics
TO victor.experiment_metrics
AS
SELECT toStartOfMinute(timestamp) AS minute_bucket,
       exp_id,
       bucket,
       layer,
       count()                    AS total_events,
       uniqExact(user_id)         AS unique_users,
       now64(3)                   AS updated_at
FROM victor.events
ARRAY JOIN exp_ids AS exp_id,
           buckets AS bucket,
           layers  AS layer
WHERE length(exp_ids) > 0
GROUP BY minute_bucket, exp_id, bucket, layer;

-- ============================================
-- 6. 物化视图: 转化事件聚合
-- ============================================
CREATE MATERIALIZED VIEW IF NOT EXISTS victor.mv_conversion_metrics
TO victor.experiment_metrics
AS
SELECT toStartOfMinute(timestamp)                                     AS minute_bucket,
       exp_id,
       bucket,
       layer,
       0                                                              AS total_events,
       0                                                              AS unique_users,
       now64(3)                                                       AS updated_at
FROM victor.events
ARRAY JOIN exp_ids AS exp_id,
           buckets AS bucket,
           layers  AS layer
WHERE length(exp_ids) > 0
  AND event_type = 'conversion'
GROUP BY minute_bucket, exp_id, bucket, layer;

-- ============================================
-- 7. 视图: 实时指标
-- ============================================
CREATE VIEW IF NOT EXISTS victor.v_realtime_metrics
AS
SELECT e.minute_bucket,
       e.exp_id,
       e.bucket,
       e.layer,
       e.total_events,
       e.unique_users,
       0                              AS conversions,
       0                              AS conversion_users,
       0.0                            AS total_revenue,
       0.0                            AS avg_revenue,
       e.updated_at
FROM victor.mv_experiment_metrics e;

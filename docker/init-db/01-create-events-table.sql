CREATE DATABASE IF NOT EXISTS victor;

CREATE TABLE IF NOT EXISTS victor.events (
    event_date Date DEFAULT toDate(timestamp),
    event_id String,
    user_id String,
    timestamp DateTime64(3),
    platform String,
    device_id String,
    session_id String,
    exp_ids Array(String),
    buckets Array(String),
    layers Array(String),
    properties String,
    received_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(event_date)
ORDER BY (event_date, user_id, timestamp)
SETTINGS index_granularity = 8192;
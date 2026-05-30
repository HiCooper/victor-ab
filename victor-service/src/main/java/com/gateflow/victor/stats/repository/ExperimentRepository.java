package com.gateflow.victor.stats.repository;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ExperimentRepository - reads experiment metadata from MySQL.
 * Uses JdbcTemplate for lightweight database access.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExperimentRepository {

    private final JdbcTemplate jdbc;

    /**
     * Find all experiments in a running or ramping state.
     */
    public List<ExperimentSummary> findRunningExperiments() {
        String sql = """
                SELECT e.id, e.exp_id, e.name, e.layer_id, e.status,
                       e.primary_metric, e.secondary_metrics, e.guardrail_metrics,
                       e.start_time, e.end_time,
                       l.layer_id AS layer_key, l.salt
                FROM victor_experiment e
                JOIN victor_layer l ON e.layer_id = l.id
                WHERE e.status IN ('running')
                """;
        return jdbc.query(sql, this::mapExperimentSummary);
    }

    /**
     * Find experiments that have been stopped and need final analysis.
     */
    public List<ExperimentSummary> findStoppingExperiments() {
        String sql = """
                SELECT e.id, e.exp_id, e.name, e.layer_id, e.status,
                       e.primary_metric, e.secondary_metrics, e.guardrail_metrics,
                       e.start_time, e.end_time,
                       l.layer_id AS layer_key, l.salt
                FROM victor_experiment e
                JOIN victor_layer l ON e.layer_id = l.id
                WHERE e.status = 'stopped'
                """;
        return jdbc.query(sql, this::mapExperimentSummary);
    }

    /**
     * Find active buckets for an experiment by its business exp_id.
     * Returns control bucket (lowest bucket_start) and treatment buckets.
     */
    public BucketInfo findBuckets(String expId) {
        String sql = """
                SELECT v.bucket_id, v.name, v.bucket_start, v.bucket_end, v.is_active
                FROM victor_bucket v
                JOIN victor_experiment e ON v.exp_id = e.id
                WHERE e.exp_id = ? AND v.is_active = TRUE
                ORDER BY v.bucket_start ASC
                """;
        List<BucketRow> rows = jdbc.query(sql, (rs, rowNum) -> {
            BucketRow row = new BucketRow();
            row.bucketKey = rs.getString("bucket_id");
            row.name = rs.getString("name");
            row.bucketStart = rs.getInt("bucket_start");
            row.bucketEnd = rs.getInt("bucket_end");
            return row;
        }, expId);

        if (rows.isEmpty()) {
            return new BucketInfo();
        }

        BucketInfo info = new BucketInfo();
        info.controlBucket = rows.get(0).bucketKey;
        info.treatmentBuckets = new ArrayList<>();
        info.bucketProportions = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            info.treatmentBuckets.add(rows.get(i).bucketKey);
        }
        info.allBucketKeys = rows.stream().map(r -> r.bucketKey).toList();

        // Compute expected traffic proportions from bucket ranges (0-9999)
        for (BucketRow row : rows) {
            int bucketSize = row.bucketEnd - row.bucketStart + 1;
            info.bucketProportions.put(row.bucketKey, bucketSize / 10000.0);
        }
        return info;
    }

    /**
     * Get experiment date range for analysis.
     */
    public ExperimentDateRange findDateRange(String expId) {
        String sql = """
                SELECT DATE(start_time) AS start_date, DATE(end_time) AS end_date
                FROM victor_experiment
                WHERE exp_id = ?
                """;
        return jdbc.queryForObject(sql, (rs, rowNum) -> {
            ExperimentDateRange range = new ExperimentDateRange();
            range.startDate = rs.getDate("start_date") != null
                    ? rs.getDate("start_date").toLocalDate() : LocalDate.now().minusDays(7);
            range.endDate = rs.getDate("end_date") != null
                    ? rs.getDate("end_date").toLocalDate() : LocalDate.now();
            return range;
        }, expId);
    }

    private ExperimentSummary mapExperimentSummary(ResultSet rs, int rowNum) throws SQLException {
        ExperimentSummary s = new ExperimentSummary();
        s.id = rs.getLong("id");
        s.expId = rs.getString("exp_id");
        s.name = rs.getString("name");
        s.status = rs.getString("status");
        s.layerKey = rs.getString("layer_key");
        s.salt = rs.getString("salt");
        s.primaryMetric = rs.getString("primary_metric");
        s.guardrailMetrics = rs.getString("guardrail_metrics");
        s.startTime = rs.getTimestamp("start_time") != null
                ? rs.getTimestamp("start_time").toLocalDateTime() : null;
        s.endTime = rs.getTimestamp("end_time") != null
                ? rs.getTimestamp("end_time").toLocalDateTime() : null;
        return s;
    }

    @Data
    public static class ExperimentSummary {
        private Long id;
        private String expId;
        private String name;
        private String status;
        private String layerKey;
        private String salt;
        private String primaryMetric;
        private String guardrailMetrics;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    public static class BucketInfo {
        private String controlBucket;
        private List<String> treatmentBuckets = List.of();
        private List<String> allBucketKeys = List.of();
        private Map<String, Double> bucketProportions = Map.of();
    }

    @Data
    public static class ExperimentDateRange {
        private LocalDate startDate;
        private LocalDate endDate;
    }

    private static class BucketRow {
        String bucketKey;
        String name;
        Integer bucketStart;
        Integer bucketEnd;
    }
}

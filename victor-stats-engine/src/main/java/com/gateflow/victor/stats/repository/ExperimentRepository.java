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
            WHERE e.status IN ('running', 'ramp')
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
     * Find active variants for an experiment by its business exp_id.
     * Returns control variant (lowest bucket_start) and treatment variants.
     */
    public VariantInfo findVariants(String expId) {
        String sql = """
            SELECT v.variant_key, v.name, v.bucket_start, v.bucket_end, v.is_active
            FROM victor_variant v
            JOIN victor_experiment e ON v.exp_id = e.id
            WHERE e.exp_id = ? AND v.is_active = TRUE
            ORDER BY v.bucket_start ASC
            """;
        List<VariantRow> rows = jdbc.query(sql, (rs, rowNum) -> {
            VariantRow row = new VariantRow();
            row.variantKey = rs.getString("variant_key");
            row.name = rs.getString("name");
            row.bucketStart = rs.getInt("bucket_start");
            row.bucketEnd = rs.getInt("bucket_end");
            return row;
        }, expId);

        if (rows.isEmpty()) {
            return new VariantInfo();
        }

        VariantInfo info = new VariantInfo();
        info.controlVariant = rows.get(0).variantKey;
        info.treatmentVariants = new ArrayList<>();
        info.bucketProportions = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            info.treatmentVariants.add(rows.get(i).variantKey);
        }
        info.allVariantKeys = rows.stream().map(r -> r.variantKey).toList();

        // Compute expected traffic proportions from bucket ranges (0-9999)
        for (VariantRow row : rows) {
            int bucketSize = row.bucketEnd - row.bucketStart + 1;
            info.bucketProportions.put(row.variantKey, bucketSize / 10000.0);
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
    public static class VariantInfo {
        private String controlVariant;
        private List<String> treatmentVariants = List.of();
        private List<String> allVariantKeys = List.of();
        private Map<String, Double> bucketProportions = Map.of();
    }

    @Data
    public static class ExperimentDateRange {
        private LocalDate startDate;
        private LocalDate endDate;
    }

    private static class VariantRow {
        String variantKey;
        String name;
        Integer bucketStart;
        Integer bucketEnd;
    }
}

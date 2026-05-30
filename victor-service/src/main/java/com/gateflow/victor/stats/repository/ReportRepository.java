package com.gateflow.victor.stats.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.stats.model.*;
import java.sql.ResultSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

/**
 * ReportRepository - persists ExperimentReport to MySQL and provides read access.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Save or update an experiment report for the given date.
     */
    public void saveReport(ExperimentReport report, LocalDate reportDate) {
        saveReport(report, reportDate, false);
    }

    /**
     * Save or update an experiment report with CUPED flag.
     */
    public void saveReport(ExperimentReport report, LocalDate reportDate, boolean cupedApplied) {
        try {
            String secondaryJson = report.getSecondaryMetrics() != null
                ? objectMapper.writeValueAsString(report.getSecondaryMetrics()) : null;
            String guardrailJson = report.getGuardrailMetrics() != null
                ? objectMapper.writeValueAsString(report.getGuardrailMetrics()) : null;
            String summariesJson = report.getBucketSummaries() != null
                ? objectMapper.writeValueAsString(report.getBucketSummaries()) : null;
            String trendsJson = report.getDailyTrends() != null
                ? objectMapper.writeValueAsString(report.getDailyTrends()) : null;

            String sql = """
                INSERT INTO victor_experiment_report
                    (exp_id, report_date,
                     srm_passed, srm_p_value, srm_message,
                     primary_metric_name, primary_p_value,
                     primary_lift, primary_lift_ci_lower, primary_lift_ci_upper,
                     primary_significant,
                     secondary_results_json, guardrail_results_json,
                     bucket_summaries_json, daily_trends_json,
                     recommendation, recommendation_reason,
                     cuped_applied, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    srm_passed = VALUES(srm_passed),
                    srm_p_value = VALUES(srm_p_value),
                    srm_message = VALUES(srm_message),
                    primary_p_value = VALUES(primary_p_value),
                    primary_lift = VALUES(primary_lift),
                    primary_lift_ci_lower = VALUES(primary_lift_ci_lower),
                    primary_lift_ci_upper = VALUES(primary_lift_ci_upper),
                    primary_significant = VALUES(primary_significant),
                    secondary_results_json = VALUES(secondary_results_json),
                    guardrail_results_json = VALUES(guardrail_results_json),
                    bucket_summaries_json = VALUES(bucket_summaries_json),
                    daily_trends_json = VALUES(daily_trends_json),
                    recommendation = VALUES(recommendation),
                    recommendation_reason = VALUES(recommendation_reason),
                    cuped_applied = VALUES(cuped_applied),
                    generated_at = VALUES(generated_at),
                    updated_at = CURRENT_TIMESTAMP
                """;

            jdbc.update(sql,
                report.getExpId(), reportDate,
                report.getSrmCheck() != null && report.getSrmCheck().isPassed(),
                report.getSrmCheck() != null ? report.getSrmCheck().getPValue() : null,
                report.getSrmCheck() != null ? report.getSrmCheck().getMessage() : null,
                report.getPrimaryMetric() != null ? report.getPrimaryMetric().getTestName() : "conversion_rate",
                report.getPrimaryMetric() != null ? report.getPrimaryMetric().getPValue() : null,
                report.getPrimaryMetric() != null && report.getPrimaryMetric().getLift() != null
                    ? report.getPrimaryMetric().getLift().getValue() : null,
                report.getPrimaryMetric() != null && report.getPrimaryMetric().getLift() != null
                    ? report.getPrimaryMetric().getLift().getConfidenceIntervalLower() : null,
                report.getPrimaryMetric() != null && report.getPrimaryMetric().getLift() != null
                    ? report.getPrimaryMetric().getLift().getConfidenceIntervalUpper() : null,
                report.getPrimaryMetric() != null && report.getPrimaryMetric().isSignificant(),
                secondaryJson,
                guardrailJson,
                summariesJson,
                trendsJson,
                report.getRecommendation() != null ? report.getRecommendation().name() : null,
                report.getRecommendationReason(),
                cupedApplied,
                report.getGeneratedAt()
            );

            log.debug("Saved report for experiment {} on {}", report.getExpId(), reportDate);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize report JSON for expId={}", report.getExpId(), e);
        }
    }

    /**
     * Update experiment status in victor_experiment table.
     */
    public void updateExperimentStatus(String expId, String newStatus) {
        String sql = "UPDATE victor_experiment SET status = ? WHERE exp_id = ?";
        int rows = jdbc.update(sql, newStatus, expId);
        if (rows > 0) {
            log.info("Updated experiment {} status to {}", expId, newStatus);
        } else {
            log.warn("No rows updated for experiment {} status change to {}", expId, newStatus);
        }
    }

    /**
     * Find the latest report for an experiment (reads pre-computed results).
     */
    public ExperimentReport findLatestReport(String expId) {
        String sql = """
            SELECT exp_id, report_date,
                   srm_passed, srm_p_value, srm_message,
                   primary_metric_name, primary_p_value,
                   primary_lift, primary_lift_ci_lower, primary_lift_ci_upper,
                   primary_significant,
                   secondary_results_json, guardrail_results_json,
                   bucket_summaries_json, daily_trends_json,
                   recommendation, recommendation_reason,
                   cuped_applied, generated_at
            FROM victor_experiment_report
            WHERE exp_id = ?
            ORDER BY report_date DESC
            LIMIT 1
            """;

        List<ExperimentReport> reports = jdbc.query(sql, (rs, rowNum) -> {
            try {
                ExperimentReport.SrmCheckResult srm = ExperimentReport.SrmCheckResult.builder()
                    .passed(rs.getBoolean("srm_passed"))
                    .pValue(rs.getDouble("srm_p_value"))
                    .message(rs.getString("srm_message"))
                    .build();

                TestResult primary = null;
                double pValue = rs.getDouble("primary_p_value");
                if (!rs.wasNull()) {
                    double lift = rs.getDouble("primary_lift");
                    double liftLower = rs.getDouble("primary_lift_ci_lower");
                    double liftUpper = rs.getDouble("primary_lift_ci_upper");

                    primary = TestResult.builder()
                        .testName(rs.getString("primary_metric_name"))
                        .pValue(pValue)
                        .significant(rs.getBoolean("primary_significant"))
                        .lift(LiftEstimate.of(lift, liftLower, liftUpper))
                        .build();
                }

                List<TestResult> secondary = parseJson(rs.getString("secondary_results_json"),
                    new TypeReference<List<TestResult>>() {});
                List<SequentialTestResult> guardrail = parseJson(rs.getString("guardrail_results_json"),
                    new TypeReference<List<SequentialTestResult>>() {});
                Map<String, ExperimentReport.BucketSummary> summaries = parseJson(
                    rs.getString("bucket_summaries_json"),
                    new TypeReference<Map<String, ExperimentReport.BucketSummary>>() {});
                Map<String, List<ExperimentReport.DailyMetric>> trends = parseJson(
                    rs.getString("daily_trends_json"),
                    new TypeReference<Map<String, List<ExperimentReport.DailyMetric>>>() {});

                String recStr = rs.getString("recommendation");
                Recommendation recommendation = recStr != null ? Recommendation.valueOf(recStr) : null;

                return ExperimentReport.builder()
                    .expId(rs.getString("exp_id"))
                    .startDate(rs.getDate("report_date").toLocalDate())
                    .endDate(rs.getDate("report_date").toLocalDate())
                    .srmCheck(srm)
                    .primaryMetric(primary)
                    .secondaryMetrics(secondary != null ? secondary : List.of())
                    .guardrailMetrics(guardrail != null ? guardrail : List.of())
                    .bucketSummaries(summaries != null ? summaries : Map.of())
                    .dailyTrends(trends != null ? trends : Map.of())
                    .recommendation(recommendation)
                    .recommendationReason(rs.getString("recommendation_reason"))
                    .generatedAt(rs.getLong("generated_at"))
                    .build();
            } catch (Exception e) {
                log.error("Failed to deserialize report for expId={}", expId, e);
                return null;
            }
        }, expId);

        return reports.isEmpty() ? null : reports.get(0);
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON column: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save CUPED-adjusted values per bucket for a given date.
     * Uses ON DUPLICATE KEY UPDATE for idempotent daily writes.
     */
    public void saveCupedValues(String expId, LocalDate reportDate,
            Map<String, ExperimentReport.BucketSummary> cupedSummaries) {
        String sql = """
            INSERT INTO victor_cuped_values
                (exp_id, report_date, bucket, cuped_adjusted_mean, cuped_adjusted_variance)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                cuped_adjusted_mean = VALUES(cuped_adjusted_mean),
                cuped_adjusted_variance = VALUES(cuped_adjusted_variance)
            """;

        for (Map.Entry<String, ExperimentReport.BucketSummary> entry : cupedSummaries.entrySet()) {
            ExperimentReport.BucketSummary s = entry.getValue();
            jdbc.update(sql, expId, reportDate, entry.getKey(),
                s.getCupedAdjustedMean(), s.getCupedAdjustedVariance());
        }
        log.debug("Saved CUPED values for experiment {} on date {}: {} buckets",
            expId, reportDate, cupedSummaries.size());
    }

    /**
     * Find the latest CUPED-adjusted values for an experiment.
     * Returns empty map if no CUPED data exists.
     */
    public Map<String, CupedValueDto> findLatestCupedValues(String expId) {
        String sql = """
            SELECT bucket, cuped_adjusted_mean, cuped_adjusted_variance
            FROM victor_cuped_values
            WHERE exp_id = ?
              AND report_date = (SELECT MAX(report_date) FROM victor_cuped_values WHERE exp_id = ?)
            """;

        Map<String, CupedValueDto> results = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            results.put(rs.getString("bucket"),
                new CupedValueDto(
                    rs.getString("bucket"),
                    rs.getDouble("cuped_adjusted_mean"),
                    rs.getDouble("cuped_adjusted_variance")
                ));
        }, expId, expId);
        return results;
    }

    public ExperimentReport findReportByDate(String expId, LocalDate date) {
        String sql = """
            SELECT exp_id, report_date,
                   srm_passed, srm_p_value, srm_message,
                   primary_metric_name, primary_p_value,
                   primary_lift, primary_lift_ci_lower, primary_lift_ci_upper,
                   primary_significant,
                   secondary_results_json, guardrail_results_json,
                   bucket_summaries_json, daily_trends_json,
                   recommendation, recommendation_reason,
                   cuped_applied, generated_at
            FROM victor_experiment_report
            WHERE exp_id = ? AND report_date = ?
            ORDER BY report_date DESC
            LIMIT 1
            """;

        List<ExperimentReport> reports = jdbc.query(sql, (rs, rowNum) -> {
            try {
                return mapRow(rs);
            } catch (Exception e) {
                log.error("Failed to map report row", e);
                return null;
            }
        }, expId, java.sql.Date.valueOf(date));
        return reports.isEmpty() ? null : reports.get(0);
    }

    public List<LocalDate> getAvailableReportDates(String expId) {
        String sql = """
            SELECT DISTINCT report_date
            FROM victor_experiment_report
            WHERE exp_id = ?
            ORDER BY report_date DESC
            """;
        return jdbc.query(sql, (rs, rowNum) -> rs.getDate("report_date").toLocalDate(), expId);
    }

    private ExperimentReport mapRow(ResultSet rs) throws Exception {
        ExperimentReport report = ExperimentReport.builder()
            .expId(rs.getString("exp_id"))
            .startDate(rs.getDate("report_date").toLocalDate())
            .endDate(rs.getDate("report_date").toLocalDate())
            .build();
        return report;
    }

    @Data
    @AllArgsConstructor
    public static class CupedValueDto {
        private String bucket;
        private Double cupedAdjustedMean;
        private Double cupedAdjustedVariance;
    }
}

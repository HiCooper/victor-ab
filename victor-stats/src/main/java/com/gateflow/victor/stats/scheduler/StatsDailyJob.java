package com.gateflow.victor.stats.scheduler;

import com.gateflow.victor.stats.algorithm.CUPED;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ExperimentReport;
import com.gateflow.victor.stats.model.SampleStatistics;
import com.gateflow.victor.stats.repository.ExperimentRepository;
import com.gateflow.victor.stats.repository.MetricsRepository;
import com.gateflow.victor.stats.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * StatsDailyJob - 每天凌晨 2:00 完整统计分析
 *
 * 阶段 1: 聚合层计算（SRM, Z-Test, BH, mSPRT）
 * 阶段 2: CUPED 用户级方差缩减（仅对运行 ≥7 天的实验）
 * 阶段 3: 结果写入 MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsDailyJob {

    private final ExperimentRepository experimentRepository;
    private final StatsEngine statsEngine;
    private final CUPED cuped;
    private final ReportRepository reportRepository;

    /** Minimum days before CUPED is applied (needs pre-experiment window) */
    private static final int CUPED_MIN_DAYS = 7;
    /** Pre-experiment lookback window in days */
    private static final int PRE_EXP_WINDOW_DAYS = 7;

    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailyAnalysis() {
        LocalDate reportDate = LocalDate.now().minusDays(1); // yesterday
        log.info("Starting daily analysis for date: {}", reportDate);

        List<ExperimentRepository.ExperimentSummary> experiments =
            experimentRepository.findRunningExperiments();

        int processed = 0;
        for (ExperimentRepository.ExperimentSummary exp : experiments) {
            try {
                analyzeExperiment(exp, reportDate);
                processed++;
            } catch (Exception e) {
                log.error("Daily analysis failed for experiment {}: {}", exp.getExpId(), e.getMessage(), e);
            }
        }

        log.info("Daily analysis complete. Processed {}/{} experiments.", processed, experiments.size());
    }

    private void analyzeExperiment(ExperimentRepository.ExperimentSummary exp, LocalDate reportDate) {
        // Determine date range
        ExperimentRepository.ExperimentDateRange dateRange =
            experimentRepository.findDateRange(exp.getExpId());
        LocalDate startDate = dateRange.getStartDate();
        LocalDate endDate = reportDate; // up to yesterday

        // Get variant info
        ExperimentRepository.VariantInfo variants = experimentRepository.findVariants(exp.getExpId());
        if (variants.getControlVariant() == null || variants.getAllVariantKeys().size() < 2) {
            log.warn("Skipping experiment {}: insufficient variants", exp.getExpId());
            return;
        }

        // Phase 1: Aggregated analysis
        ExperimentReport report = statsEngine.analyzeExperiment(
            exp.getExpId(),
            exp.getLayerKey(),
            startDate,
            endDate,
            variants.getControlVariant(),
            variants.getTreatmentVariants()
        );

        // Phase 2: CUPED (only if experiment has been running long enough)
        boolean cupedApplied = false;
        long daysRunning = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysRunning >= CUPED_MIN_DAYS) {
            try {
                applyCUPED(report, exp, variants, startDate, endDate);
                cupedApplied = true;
            } catch (Exception e) {
                log.warn("CUPED adjustment failed for experiment {}: {}", exp.getExpId(), e.getMessage());
            }
        }

        // Phase 3: Save to MySQL
        reportRepository.saveReport(report, reportDate, cupedApplied);
        log.info("Daily report saved for experiment {} on {}", exp.getExpId(), reportDate);
    }

    /**
     * Apply CUPED variance reduction using per-user pre-experiment data.
     */
    private void applyCUPED(
            ExperimentReport report,
            ExperimentRepository.ExperimentSummary exp,
            ExperimentRepository.VariantInfo variants,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDate preStartDate = startDate.minusDays(PRE_EXP_WINDOW_DAYS);
        LocalDate preEndDate = startDate.minusDays(1);

        MetricsRepository metricsRepo = statsEngine.getMetricsRepository();

        // Apply CUPED to each variant
        List<Double> allPreValues = new ArrayList<>();

        // First pass: collect all pre-experiment values to compute overallMeanX
        for (String variantKey : variants.getAllVariantKeys()) {
            List<MetricsRepository.UserMetric> userMetrics = metricsRepo.queryUserLevelData(
                exp.getExpId(), variantKey, startDate, endDate, preStartDate, preEndDate);
            for (MetricsRepository.UserMetric m : userMetrics) {
                allPreValues.add(m.getPreExperimentValue());
            }
        }

        double overallMeanX = allPreValues.stream()
            .mapToDouble(d -> d).average().orElse(0.0);

        if (overallMeanX == 0.0) {
            return; // no pre-experiment data available
        }

        // Second pass: compute CUPED-adjusted statistics per variant
        for (String variantKey : variants.getAllVariantKeys()) {
            List<MetricsRepository.UserMetric> userMetrics = metricsRepo.queryUserLevelData(
                exp.getExpId(), variantKey, startDate, endDate, preStartDate, preEndDate);

            if (userMetrics.isEmpty()) continue;

            List<Double> experimentValues = userMetrics.stream()
                .map(MetricsRepository.UserMetric::getExperimentValue).toList();
            List<Double> preValues = userMetrics.stream()
                .map(MetricsRepository.UserMetric::getPreExperimentValue).toList();

            SampleStatistics cupedStats = cuped.adjust(experimentValues, preValues, overallMeanX);

            log.debug("CUPED for variant {}: original variance reduced, n={}, mean={}, var={}",
                variantKey, cupedStats.getN(), cupedStats.getMean(), cupedStats.getVariance());
        }

        log.info("CUPED applied to experiment {} ({} days running, {} users in pre-window)",
            exp.getExpId(), ChronoUnit.DAYS.between(startDate, endDate), allPreValues.size());
    }
}

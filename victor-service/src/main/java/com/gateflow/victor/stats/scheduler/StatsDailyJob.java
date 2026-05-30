package com.gateflow.victor.stats.scheduler;

import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ExperimentReport;
import com.gateflow.victor.stats.repository.ExperimentRepository;
import com.gateflow.victor.stats.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * StatsDailyJob — 每天凌晨 2:00 对运行中的实验做完整统计分析。
 *
 * 跑全量管线（SRM → CUPED → Z-test → mSPRT → BH），
 * 结果写入 victor_experiment_report，按天快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsDailyJob {

    private final ExperimentRepository experimentRepository;
    private final StatsEngine statsEngine;
    private final ReportRepository reportRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailyAnalysis() {
        LocalDate reportDate = LocalDate.now().minusDays(1); // yesterday
        log.info("Starting daily full analysis for date: {}", reportDate);

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
        ExperimentRepository.ExperimentDateRange dateRange =
            experimentRepository.findDateRange(exp.getExpId());
        LocalDate startDate = dateRange.getStartDate();
        LocalDate endDate = reportDate;

        ExperimentRepository.VariantInfo variants = experimentRepository.findVariants(exp.getExpId());
        if (variants.getControlVariant() == null || variants.getAllVariantKeys().size() < 2) {
            log.warn("Skipping experiment {}: insufficient variants", exp.getExpId());
            return;
        }

        log.info("Analyzing experiment {} ({} variants, {} → {})",
            exp.getExpId(), variants.getAllVariantKeys().size(), startDate, endDate);

        ExperimentReport report = statsEngine.analyzeExperiment(
            exp.getExpId(),
            exp.getLayerKey(),
            startDate,
            endDate,
            variants.getControlVariant(),
            variants.getTreatmentVariants(),
            variants.getBucketProportions(),
            Collections.emptyList()
        );

        reportRepository.saveReport(report, reportDate);

        log.info("Daily report saved for experiment {} on {}. " +
                "SRM passed={}, primary p-value={}, recommendation={}",
            exp.getExpId(), reportDate,
            report.getSrmCheck() != null ? report.getSrmCheck().isPassed() : "N/A",
            report.getPrimaryMetric() != null ? report.getPrimaryMetric().getPValue() : "N/A",
            report.getRecommendation());
    }
}

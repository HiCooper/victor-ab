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
import java.util.List;

/**
 * EndOfExpJob - 每1分钟检测刚结束的实验，触发生成最终报告。
 *
 * 实验停止后（status='stopped'），自动生成完整分析报告，
 * 并将状态推进到 'analyzing'。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndOfExpJob {

    private final ExperimentRepository experimentRepository;
    private final StatsEngine statsEngine;
    private final ReportRepository reportRepository;

    @Scheduled(fixedRate = 60_000)
    public void checkEndedExperiments() {
        List<ExperimentRepository.ExperimentSummary> stopping =
            experimentRepository.findStoppingExperiments();

        for (ExperimentRepository.ExperimentSummary exp : stopping) {
            try {
                generateFinalReport(exp);
            } catch (Exception e) {
                log.error("Final report generation failed for experiment {}: {}", exp.getExpId(), e.getMessage(), e);
            }
        }
    }

    private void generateFinalReport(ExperimentRepository.ExperimentSummary exp) {
        log.info("Generating final report for experiment {} (status={})", exp.getExpId(), exp.getStatus());

        // Get date range
        ExperimentRepository.ExperimentDateRange dateRange =
            experimentRepository.findDateRange(exp.getExpId());
        LocalDate startDate = dateRange.getStartDate();
        LocalDate endDate = dateRange.getEndDate();
        LocalDate reportDate = LocalDate.now();

        // Get variants
        ExperimentRepository.VariantInfo variants = experimentRepository.findVariants(exp.getExpId());
        if (variants.getControlVariant() == null) {
            log.warn("Cannot generate report for experiment {}: no variants found", exp.getExpId());
            return;
        }

        // Run full analysis
        ExperimentReport report = statsEngine.analyzeExperiment(
            exp.getExpId(),
            exp.getLayerKey(),
            startDate,
            endDate,
            variants.getControlVariant(),
            variants.getTreatmentVariants()
        );

        // Save report
        reportRepository.saveReport(report, reportDate);

        // Advance status: stopped → analyzing
        reportRepository.updateExperimentStatus(exp.getExpId(), "analyzing");

        log.info("Final report generated for experiment {}. Status advanced to analyzing.", exp.getExpId());
    }
}

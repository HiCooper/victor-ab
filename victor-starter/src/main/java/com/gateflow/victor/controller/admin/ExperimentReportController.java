package com.gateflow.victor.controller.admin;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.statistics.ExperimentReportService;
import com.gateflow.victor.service.statistics.ReportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Experiment Report Controller for DS Platform
 * Provides endpoints for report generation, job management, and experiment metrics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Experiment Report API", description = "实验报告与任务管理接口")
public class ExperimentReportController {

    private final ExperimentReportService reportService;
    private final ReportJobService jobService;

    /**
     * Get experiment report by experiment ID
     */
    @GetMapping("/experiments/{expId}")
    @Operation(summary = "获取实验报告", description = "根据实验ID获取统计报告，可选日期参数查历史报告")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<Map<String, Object>> getExperimentReport(
            @PathVariable String expId,
            @RequestParam(required = false) String date) {
        log.info("Getting report for experiment: {}, date: {}", expId, date);
        try {
            Map<String, Object> report;
            if (date != null && !date.isEmpty()) {
                report = reportService.getHistoricalReport(expId, LocalDate.parse(date));
            } else {
                report = reportService.getReport(expId);
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Failed to get report for experiment: {}", expId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/experiments/{expId}/dates")
    @Operation(summary = "获取报告可用日期", description = "获取该实验所有已生成报告的日期列表")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<String>> getAvailableDates(@PathVariable String expId) {
        List<LocalDate> dates = reportService.getAvailableReportDates(expId);
        return ResponseEntity.ok(dates.stream().map(LocalDate::toString).toList());
    }

    /**
     * Get all experiment reports
     */
    @GetMapping("/experiments")
    @Operation(summary = "获取所有实验报告", description = "获取全部实验的报告列表")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<Map<String, Object>>> getAllReports() {
        log.info("Getting all experiment reports");
        List<Map<String, Object>> reports = reportService.getAllReports();
        return ResponseEntity.ok(reports);
    }

    /**
     * Manually trigger report generation
     */
    @PostMapping("/generate")
    @Operation(summary = "生成实验报告", description = "手动触发实验报告生成任务")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestBody Map<String, Object> request) {
        log.info("Triggering report generation: {}", request);
        try {
            String expId = (String) request.get("experimentId");
            String startDate = (String) request.get("startDate");
            String endDate = (String) request.get("endDate");
            Boolean includeSecondaryMetrics = (Boolean) request.getOrDefault("includeSecondaryMetrics", true);
            Boolean includeGuardrails = (Boolean) request.getOrDefault("includeGuardrails", true);

            String jobId = reportService.triggerReportGeneration(
                    expId, startDate, endDate, includeSecondaryMetrics, includeGuardrails);

            return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "status", "submitted",
                    "message", "Report generation job has been submitted"
            ));
        } catch (Exception e) {
            log.error("Failed to trigger report generation", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get all report jobs
     */
    @GetMapping("/jobs")
    @Operation(summary = "获取报告任务列表", description = "查询报告生成任务的状态")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<Map<String, Object>>> getReportJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        log.info("Getting report jobs with status: {}, type: {}", status, type);
        List<Map<String, Object>> jobs = jobService.getJobs(status, type);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get job status by ID
     */
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "获取任务状态", description = "根据任务ID查询报告生成任务的状态")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        log.info("Getting job status: {}", jobId);
        try {
            Map<String, Object> job = jobService.getJob(jobId);
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            log.error("Failed to get job status: {}", jobId, e);
            return ResponseEntity.notFound().build();
        }
    }
}

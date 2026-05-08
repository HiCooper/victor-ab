package com.gateflow.victor.controller;

import com.gateflow.victor.service.ExperimentReportService;
import com.gateflow.victor.service.ReportJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Experiment Report Controller for DS Platform
 * Provides endpoints for report generation, job management, and experiment metrics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExperimentReportController {

    private final ExperimentReportService reportService;
    private final ReportJobService jobService;

    /**
     * Get experiment report by experiment ID
     */
    @GetMapping("/experiments/{expId}")
    public ResponseEntity<Map<String, Object>> getExperimentReport(@PathVariable String expId) {
        log.info("Getting report for experiment: {}", expId);
        try {
            Map<String, Object> report = reportService.getReport(expId);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Failed to get report for experiment: {}", expId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all experiment reports
     */
    @GetMapping("/experiments")
    public ResponseEntity<List<Map<String, Object>>> getAllReports() {
        log.info("Getting all experiment reports");
        List<Map<String, Object>> reports = reportService.getAllReports();
        return ResponseEntity.ok(reports);
    }

    /**
     * Manually trigger report generation
     */
    @PostMapping("/generate")
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

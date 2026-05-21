package com.gateflow.victor.controller;

import com.gateflow.victor.service.statistics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Metrics Controller for DS Platform
 * Provides endpoints for real-time and historical metrics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Get real-time metrics for an experiment
     */
    @GetMapping("/realtime")
    public ResponseEntity<List<Map<String, Object>>> getRealtimeMetrics(
            @RequestParam String expId,
            @RequestParam(required = false, defaultValue = "60") Integer window) {
        log.info("Getting realtime metrics for experiment: {}, window: {}", expId, window);
        List<Map<String, Object>> metrics = metricsService.getRealtimeMetrics(expId, window);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get daily metrics for an experiment
     */
    @GetMapping("/daily")
    public ResponseEntity<List<Map<String, Object>>> getDailyMetrics(
            @RequestParam String expId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        log.info("Getting daily metrics for experiment: {}, start: {}, end: {}", expId, startDate, endDate);
        List<Map<String, Object>> metrics = metricsService.getDailyMetrics(expId, startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get dashboard statistics
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        log.info("Getting dashboard statistics");
        Map<String, Object> stats = metricsService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get event details for an experiment
     */
    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> getEventDetails(
            @RequestParam String expId,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        log.info("Getting event details for experiment: {}, limit: {}, offset: {}", expId, limit, offset);
        List<Map<String, Object>> events = metricsService.getEventDetails(expId, limit, offset);
        return ResponseEntity.ok(events);
    }
}

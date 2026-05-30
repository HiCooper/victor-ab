package com.gateflow.victor.controller.admin;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.statistics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Metrics Controller for DS Platform
 * Provides endpoints for real-time and historical metrics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/realtime")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<Map<String, Object>>> getRealtimeMetrics(
            @RequestParam String expId,
            @RequestParam(required = false, defaultValue = "60") Integer window) {
        log.info("Getting realtime metrics for experiment: {}, window: {}", expId, window);
        List<Map<String, Object>> metrics = metricsService.getRealtimeMetrics(expId, window);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/realtime/trend")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<Map<String, Object>>> getRealtimeTrend(
            @RequestParam String expId,
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Getting realtime trend for experiment: {}, hours: {}", expId, hours);
        List<Map<String, Object>> trend = metricsService.getRealtimeTrend(expId, hours);
        return ResponseEntity.ok(trend);
    }

    @GetMapping("/daily")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<Map<String, Object>>> getDailyMetrics(
            @RequestParam String expId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        log.info("Getting daily metrics for experiment: {}, start: {}, end: {}", expId, startDate, endDate);
        List<Map<String, Object>> metrics = metricsService.getDailyMetrics(expId, startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/dashboard")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        log.info("Getting dashboard statistics");
        Map<String, Object> stats = metricsService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/events")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<List<Map<String, Object>>> getEventDetails(
            @RequestParam String expId,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        log.info("Getting event details for experiment: {}, limit: {}, offset: {}", expId, limit, offset);
        List<Map<String, Object>> events = metricsService.getEventDetails(expId, limit, offset);
        return ResponseEntity.ok(events);
    }
}

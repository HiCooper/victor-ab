package com.gateflow.victor.controller.admin;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.BanditRequest;
import com.gateflow.victor.domain.dto.BanditResponse;
import com.gateflow.victor.domain.dto.BayesianAnalysisRequest;
import com.gateflow.victor.domain.dto.BayesianAnalysisResponse;
import com.gateflow.victor.domain.dto.PowerAnalysisRequest;
import com.gateflow.victor.domain.dto.SubgroupAnalysisResponse;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.analysis.BayesianAnalysisService;
import com.gateflow.victor.service.analysis.PowerAnalysisService;
import com.gateflow.victor.service.analysis.SubgroupAnalysisService;
import com.gateflow.victor.service.bandit.BanditService;
import com.gateflow.victor.service.bucket.BucketService;
import com.gateflow.victor.stats.repository.MetricsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Analysis Controller
 * Consolidates bandit, bayesian, power, and subgroup analysis endpoints
 * previously scattered across BanditController, BayesianAnalysisController,
 * PowerAnalysisController, and SubgroupAnalysisController.
 */
@RestController
@RequestMapping("/api/v1/admin/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis API", description = "统一分析接口：多臂老虎机、贝叶斯、功效分析、人群拆分")
public class AnalysisController {

    private final BanditService banditService;
    private final BucketService bucketService;
    private final MetricsRepository metricsRepository;
    private final BayesianAnalysisService bayesianAnalysisService;
    private final PowerAnalysisService powerAnalysisService;
    private final SubgroupAnalysisService subgroupAnalysisService;

    // ────────────────────────── Bandit ──────────────────────────

    @PostMapping("/bandit/optimize")
    @Operation(summary = "流量优化建议", description = "基于多臂老虎机算法给出流量分配建议")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<BanditResponse> banditOptimize(@Valid @RequestBody BanditRequest request) {
        List<Bucket> buckets = bucketService.getBucketsByExperimentId(request.getExperimentId());
        if (buckets == null || buckets.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String businessExpId = buckets.get(0).getExpId();
        Map<Long, int[]> data = new HashMap<>();
        Map<String, MetricsRepository.BucketStats> bucketStats =
                metricsRepository.queryExperimentStats(
                        businessExpId, LocalDate.now().minusDays(7), LocalDate.now());

        for (Bucket v : buckets) {
            String bucketKey = v.getBucketId() != null ? v.getBucketId() : v.getName();
            MetricsRepository.BucketStats stats = bucketStats.get(bucketKey);
            if (stats != null && stats.getTotalUsers() > 0) {
                data.put(v.getId(), new int[]{
                        (int) stats.getTotalUsers(),
                        (int) stats.getTotalConversions()
                });
            } else {
                data.put(v.getId(), new int[]{0, 0});
            }
        }

        BanditResponse response = banditService.optimize(request, buckets, data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bandit/algorithms")
    @Operation(summary = "获取可用算法列表", description = "返回支持的多臂老虎机算法及说明")
    public ResponseEntity<Map<String, String>> banditAlgorithms() {
        Map<String, String> algorithms = new HashMap<>();
        algorithms.put("EPSILON_GREEDY", "Epsilon-Greedy: 简单有效的探索-利用平衡算法");
        algorithms.put("UCB", "UCB: 置信上界算法，自动平衡探索与利用");
        algorithms.put("THOMPSON_SAMPLING", "Thompson Sampling: 贝叶斯方法，全最优分配");
        return ResponseEntity.ok(algorithms);
    }

    // ──────────────────────── Bayesian ──────────────────────────

    @PostMapping("/bayesian")
    @Operation(summary = "执行贝叶斯分析", description = "基于贝叶斯方法对实验数据进行统计分析")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<BayesianAnalysisResponse> bayesianAnalyze(
            @Valid @RequestBody BayesianAnalysisRequest request) {
        BayesianAnalysisResponse response = bayesianAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }

    // ───────────────────────── Power ────────────────────────────

    @PostMapping("/power/calculate")
    @Operation(summary = "计算样本量", description = "根据基准转化率、MDE、alpha、power 计算每组所需样本量")
    @RequirePermission(Permission.POWER_ANALYSIS)
    public ResponseEntity<Map<String, Object>> powerCalculate(
            @Valid @RequestBody PowerAnalysisRequest request) {
        return ResponseEntity.ok(powerAnalysisService.runPowerAnalysis(request));
    }

    @GetMapping("/power/duration")
    @Operation(summary = "预估实验天数", description = "根据样本量和日均UV预估实验运行天数")
    public ResponseEntity<Map<String, Object>> powerDuration(
            @RequestParam long sampleSizePerGroup,
            @RequestParam(defaultValue = "50") int controlTrafficPercent,
            @RequestParam long dailyUsers) {
        long totalSampleSize = powerAnalysisService.calculateTotalSampleSize(
                0.1, 0.05, 0.05, 0.8, controlTrafficPercent);
        int days = powerAnalysisService.estimateExperimentDuration(
                sampleSizePerGroup, controlTrafficPercent, dailyUsers);

        return ResponseEntity.ok(Map.of(
                "sampleSizePerGroup", sampleSizePerGroup,
                "dailyUsers", dailyUsers,
                "estimatedDays", days,
                "minimumDays", Math.max(days, 7)
        ));
    }

    // ─────────────────────── Subgroup ───────────────────────────

    @GetMapping("/subgroup/{expId}/platform")
    @Operation(summary = "按平台拆分分析", description = "分析实验效果在不同平台（iOS/Android/Web）的差异")
    public ResponseEntity<SubgroupAnalysisResponse> subgroupByPlatform(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(subgroupAnalysisService.analyzeByPlatform(expId, startDate, endDate));
    }

    @GetMapping("/subgroup/{expId}/dimension")
    @Operation(summary = "按自定义维度拆分分析", description = "分析实验效果在任意用户属性维度的差异")
    public ResponseEntity<SubgroupAnalysisResponse> subgroupByDimension(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "拆分维度") @RequestParam String dimension,
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(subgroupAnalysisService.analyzeByDimension(expId, dimension, startDate, endDate));
    }
}

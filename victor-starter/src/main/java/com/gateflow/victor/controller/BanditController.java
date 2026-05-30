package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.BanditRequest;
import com.gateflow.victor.domain.dto.BanditResponse;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.service.bandit.BanditService;
import com.gateflow.victor.service.bucket.BucketService;
import com.gateflow.victor.stats.repository.MetricsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多臂老虎机控制器
 */
@RestController
@RequestMapping("/api/v1/bandit")
@RequiredArgsConstructor
@Tag(name = "Bandit API", description = "多臂老虎机流量优化接口")
public class BanditController {

    private final BanditService banditService;
    private final BucketService bucketService;
    private final MetricsRepository metricsRepository;

    /**
     * 获取流量优化建议
     */
    @PostMapping("/optimize")
    @Operation(summary = "流量优化建议", description = "基于多臂老虎机算法给出流量分配建议")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<BanditResponse> optimize(@Valid @RequestBody BanditRequest request) {
        // 获取buckets (简化处理)
        List<Bucket> buckets = bucketService.getBucketsByExperimentId(request.getExperimentId());
        if (buckets == null || buckets.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Query real conversion data from ClickHouse per bucket
        String businessExpId = buckets.get(0).getExpId();
        Map<Long, int[]> data = new HashMap<>();
        Map<String, MetricsRepository.BucketStats> bucketStats =
            metricsRepository.queryExperimentStats(
                businessExpId, LocalDate.now().minusDays(7), LocalDate.now());

        for (Bucket v : buckets) {
            // Match MySQL bucket key (bucketId) to ClickHouse bucket name
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

    /**
     * 获取可用的算法列表
     */
    @GetMapping("/algorithms")
    @Operation(summary = "获取可用算法列表", description = "返回支持的多臂老虎机算法及说明")
    public ResponseEntity<Map<String, String>> getAlgorithms() {
        Map<String, String> algorithms = new HashMap<>();
        algorithms.put("EPSILON_GREEDY", "Epsilon-Greedy: 简单有效的探索-利用平衡算法");
        algorithms.put("UCB", "UCB: 置信上界算法，自动平衡探索与利用");
        algorithms.put("THOMPSON_SAMPLING", "Thompson Sampling: 贝叶斯方法，全最优分配");
        return ResponseEntity.<Map<String, String>>ok(algorithms);
    }
}
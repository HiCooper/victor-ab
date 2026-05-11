package com.gateflow.victor.controller;

import com.gateflow.victor.domain.dto.BanditRequest;
import com.gateflow.victor.domain.dto.BanditResponse;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.service.bandit.BanditService;
import com.gateflow.victor.service.variant.VariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多臂老虎机控制器
 */
@RestController
@RequestMapping("/api/v1/bandit")
@RequiredArgsConstructor
public class BanditController {

    private final BanditService banditService;
    private final VariantService variantService;

    /**
     * 获取流量优化建议
     */
    @PostMapping("/optimize")
    public ResponseEntity<BanditResponse> optimize(@RequestBody BanditRequest request) {
        // 获取variants (简化处理)
        List<Variant> variants = variantService.getVariantsByExperimentId(request.getExperimentId());
        if (variants == null || variants.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 模拟数据 (实际应该从MetricsService获取)
        Map<Long, int[]> data = new HashMap<>();
        for (Variant v : variants) {
            data.put(v.getId(), new int[]{1000, (int) (Math.random() * 100)});
        }

        BanditResponse response = banditService.optimize(request, variants, data);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取可用的算法列表
     */
    @GetMapping("/algorithms")
    public ResponseEntity<Map<String, String>> getAlgorithms() {
        Map<String, String> algorithms = new HashMap<>();
        algorithms.put("EPSILON_GREEDY", "Epsilon-Greedy: 简单有效的探索-利用平衡算法");
        algorithms.put("UCB", "UCB: 置信上界算法，自动平衡探索与利用");
        algorithms.put("THOMPSON_SAMPLING", "Thompson Sampling: 贝叶斯方法，全最优分配");
        return ResponseEntity.<Map<String, String>>ok(algorithms);
    }
}
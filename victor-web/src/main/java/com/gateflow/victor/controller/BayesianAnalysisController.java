package com.gateflow.victor.controller;

import com.gateflow.victor.domain.dto.BayesianAnalysisRequest;
import com.gateflow.victor.domain.dto.BayesianAnalysisResponse;
import com.gateflow.victor.service.analysis.BayesianAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 贝叶斯分析控制器
 */
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class BayesianAnalysisController {

    private final BayesianAnalysisService bayesianAnalysisService;

    /**
     * 执行贝叶斯分析
     */
    @PostMapping("/bayesian")
    public ResponseEntity<BayesianAnalysisResponse> analyze(
            @RequestBody BayesianAnalysisRequest request) {
        BayesianAnalysisResponse response = bayesianAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
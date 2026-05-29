package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.BayesianAnalysisRequest;
import com.gateflow.victor.domain.dto.BayesianAnalysisResponse;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.analysis.BayesianAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 贝叶斯分析控制器
 */
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Bayesian Analysis API", description = "贝叶斯统计分析接口")
public class BayesianAnalysisController {

    private final BayesianAnalysisService bayesianAnalysisService;

    /**
     * 执行贝叶斯分析
     */
    @PostMapping("/bayesian")
    @Operation(summary = "执行贝叶斯分析", description = "基于贝叶斯方法对实验数据进行统计分析")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<BayesianAnalysisResponse> analyze(
            @Valid @RequestBody BayesianAnalysisRequest request) {
        BayesianAnalysisResponse response = bayesianAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}

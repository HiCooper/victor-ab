package com.gateflow.victor.controller;

import com.gateflow.victor.bucketing.BucketResult;
import com.gateflow.victor.domain.dto.BucketingResponse;
import com.gateflow.victor.service.bucketing.BucketingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分桶计算API Controller
 */
@RestController
@RequestMapping("/api/v1/bucketing")
@RequiredArgsConstructor
@Tag(name = "Bucketing API", description = "分桶计算接口")
public class BucketingController {

    private final BucketingService bucketingService;

    /**
     * 单实验分桶查询
     *
     * @param userId        用户ID
     * @param experimentKey 实验标识
     * @return 分桶结果
     */
    @GetMapping("/variant")
    @Operation(summary = "获取用户实验版本", description = "查询用户在指定实验中的分配版本")
    public BucketingResponse getVariant(
            @Parameter(description = "用户ID") @RequestParam String userId,
            @Parameter(description = "实验标识") @RequestParam String experimentKey) {

        BucketResult result = bucketingService.getVariant(userId, experimentKey);

        BucketingResponse response = new BucketingResponse();
        response.setExperimentKey(experimentKey);
        response.setVariant(result.getVariantOrNull());

        return response;
    }

    /**
     * 批量分桶查询 - 获取用户所有实验结果
     *
     * @param userId 用户ID
     * @return 所有实验的分桶结果
     */
    @GetMapping("/all-variants")
    @Operation(summary = "获取用户所有实验版本", description = "查询用户在所有运行中实验的分配结果")
    public List<BucketingResponse> getAllVariants(
            @Parameter(description = "用户ID") @RequestParam String userId) {

        List<BucketResult> results = bucketingService.getAllVariants(userId);

        return results.stream()
                .map(r -> new BucketingResponse(r.getExperimentKey(), r.getVariantOrNull(), null))
                .collect(Collectors.toList());
    }
}
package com.gateflow.victor.controller.sdk;

import com.gateflow.victor.config.RateLimit;
import com.gateflow.victor.domain.dto.ConfigResponse;
import com.gateflow.victor.domain.dto.VersionCheckResponse;
import com.gateflow.victor.service.config.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置下发API Controller
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Config API", description = "配置下发接口")
public class ConfigController {

    private final ConfigService configService;

    /**
     * 版本查询接口 - SDK快速比对版本
     *
     * @param version  SDK当前版本号
     * @param platform 平台标识
     * @return 200有更新，304无更新
     */
    @GetMapping("/version")
    @RateLimit(20)
    @Operation(summary = "查询配置版本", description = "SDK比对版本，判断是否有更新")
    public ResponseEntity<VersionCheckResponse> checkVersion(
            @Parameter(description = "当前版本号") @RequestParam(required = false) String version,
            @Parameter(description = "平台标识") @RequestParam String platform) {

        ConfigService.VersionInfo latest = configService.getLatestVersion();

        if (version != null && version.equals(latest.getVersion())) {
            // 版本一致，无更新
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // 有更新，返回最新版本号
        VersionCheckResponse response = new VersionCheckResponse();
        response.setVersion(latest.getVersion());
        response.setTimestamp(latest.getTimestamp());

        return ResponseEntity.ok(response);
    }

    /**
     * 配置拉取接口 - SDK获取增量或全量配置
     *
     * @param fromVersion SDK当前版本号(用于增量拉取)
     * @param platform    平台标识
     * @return 配置响应
     */
    @GetMapping("/fetch")
    @RateLimit(10)
    @Operation(summary = "拉取配置", description = "SDK拉取实验配置，支持全量和增量")
    public ConfigResponse fetchConfig(
            @Parameter(description = "起始版本号(增量拉取)") @RequestParam(required = false) String fromVersion,
            @Parameter(description = "平台标识") @RequestParam String platform) {

        if (fromVersion != null) {
            // 增量拉取
            return configService.getIncrementalConfig(fromVersion, platform);
        }

        // 全量拉取
        return configService.getFullConfig(platform);
    }
}
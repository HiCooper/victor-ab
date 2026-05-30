package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本检查响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionCheckResponse {

    /**
     * 最新配置版本号
     */
    private String version;

    /**
     * 版本时间戳（毫秒）
     */
    private Long timestamp;
}

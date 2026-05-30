package com.gateflow.victor.pipeline.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {
    @NotBlank(message = "eventId不能为空")
    private String eventId;

    @NotBlank(message = "userId不能为空")
    private String userId;

    @NotNull(message = "timestamp不能为空")
    private Long timestamp;

    private String platform;
    private String deviceId;
    private String sessionId;

    private List<ExperimentTagDTO> experimentTags;
    private Map<String, Object> properties;
}
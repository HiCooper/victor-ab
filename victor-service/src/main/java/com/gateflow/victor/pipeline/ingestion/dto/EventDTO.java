package com.gateflow.victor.pipeline.ingestion.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {
    @NotBlank(message = "eventId不能为空")
    private String eventId;
    
    @NotBlank(message = "eventType不能为空")
    private String eventType;
    
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
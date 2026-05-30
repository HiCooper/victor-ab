package com.gateflow.victor.pipeline.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentTagDTO {
    private String expId;
    private String bucket;
    private String layer;
}
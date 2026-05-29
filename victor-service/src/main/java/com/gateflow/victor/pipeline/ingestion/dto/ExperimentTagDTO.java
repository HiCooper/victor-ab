package com.gateflow.victor.pipeline.ingestion.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentTagDTO {
    private String expId;
    private String variant;
    private String layer;
}
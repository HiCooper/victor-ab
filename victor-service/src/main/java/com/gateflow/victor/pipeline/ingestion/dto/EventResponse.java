package com.gateflow.victor.pipeline.ingestion.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EventResponse {
    private int accepted;
    private int rejected;
    private List<String> errors;
}
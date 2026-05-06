package com.gateflow.victor.pipeline.ingestion.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class EventResponse {
    private int accepted;
    private int rejected;
    private List<String> errors;
}
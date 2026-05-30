package com.gateflow.victor.pipeline.ingestion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EventRequest {
    @Valid
    @Size(max = 100, message = "单次最多上报100条事件")
    private List<EventDTO> events;
}
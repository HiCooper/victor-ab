package com.gateflow.victor.pipeline.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "victor.pipeline")
public class PipelineProperties {
    private String kafkaTopic = "victor-events";
    private int batchSize = 100;
}
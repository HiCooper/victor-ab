package com.gateflow.victor.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Victor 应用健康检查
 */
@Component("victor")
public class VictorHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up()
            .withDetail("service", "victor-ab")
            .withDetail("status", "running")
            .build();
    }
}

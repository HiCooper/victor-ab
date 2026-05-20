package com.gateflow.victor.stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VictorStatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VictorStatsApplication.class, args);
    }
}

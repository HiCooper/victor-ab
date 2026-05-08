package com.gateflow.victor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Victor AB Experiment System 启动类
 */
@SpringBootApplication(scanBasePackages = "com.gateflow.victor")
@EnableScheduling
public class VictorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VictorServiceApplication.class, args);
    }
}
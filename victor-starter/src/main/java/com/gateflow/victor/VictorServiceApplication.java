package com.gateflow.victor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * Victor AB Experiment System 启动类
 */
@SpringBootApplication(scanBasePackages = "com.gateflow.victor")
public class VictorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VictorServiceApplication.class, args);
    }
}
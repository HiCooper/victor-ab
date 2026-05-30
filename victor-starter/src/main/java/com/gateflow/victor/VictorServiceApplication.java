package com.gateflow.victor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.gateflow.victor")
public class VictorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VictorServiceApplication.class, args);
    }
}
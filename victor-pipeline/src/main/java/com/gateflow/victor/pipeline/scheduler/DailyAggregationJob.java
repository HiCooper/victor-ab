package com.gateflow.victor.pipeline.scheduler;

import com.gateflow.victor.pipeline.aggregation.MetricsAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily aggregation job for T+1 data processing
 * Runs at 2:00 AM every day to aggregate previous day's data
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyAggregationJob {

    private final MetricsAggregator metricsAggregator;

    /**
     * Run daily aggregation at 2:00 AM every day
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailyAggregation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting daily aggregation for date: {}", yesterday);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Aggregate daily metrics
            int experimentsProcessed = metricsAggregator.aggregateDailyMetrics(yesterday);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Daily aggregation completed. Processed {} experiments in {} ms", 
                    experimentsProcessed, duration);
        } catch (Exception e) {
            log.error("Daily aggregation failed for date: {}", yesterday, e);
        }
    }

    /**
     * Manual trigger for daily aggregation (for testing or backfill)
     */
    public void runForDate(LocalDate date) {
        log.info("Manual trigger for daily aggregation for date: {}", date);
        
        try {
            long startTime = System.currentTimeMillis();
            int experimentsProcessed = metricsAggregator.aggregateDailyMetrics(date);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Manual aggregation completed. Processed {} experiments in {} ms", 
                    experimentsProcessed, duration);
        } catch (Exception e) {
            log.error("Manual aggregation failed for date: {}", date, e);
            throw e;
        }
    }
}

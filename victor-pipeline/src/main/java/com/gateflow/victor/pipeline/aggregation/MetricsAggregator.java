package com.gateflow.victor.pipeline.aggregation;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MetricsAggregator - 定时聚合任务
 * 
 * 策略：使用 Spring Scheduler 定时执行聚合（简化方案）
 * - 每分钟触发一次
 * - 从 events 表查询最近分钟的数据
 * - 聚合到 experiment_metrics 表
 * 
 * 备选方案（Flink 流处理）：
 * - 实时处理 Kafka 事件流
 * - 使用事件时间窗口
 * - 状态后端存储中间结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsAggregator {
    
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    private final ClickHouseDataSourceFactory dataSourceFactory;
    
    /**
     * 每分钟执行一次聚合任务
     * 聚合最近一分钟的事件数据
     */
    @Scheduled(fixedRate = 60000) // 60秒 = 1分钟
    public void aggregateMinuteMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minuteAgo = now.minusMinutes(1);
        
        String minuteBucket = minuteAgo.format(MINUTE_FORMATTER) + ":00";
        
        log.info("Starting metrics aggregation for minute: {}", minuteBucket);
        
        try {
            List<AggregatedMetric> metrics = queryAndAggregate(minuteBucket, minuteAgo);
            
            if (!metrics.isEmpty()) {
                persistMetrics(metrics);
                log.info("Successfully aggregated {} experiment-variant combinations", metrics.size());
            } else {
                log.debug("No metrics to aggregate for minute: {}", minuteBucket);
            }
        } catch (Exception e) {
            log.error("Failed to aggregate metrics for minute: {}", minuteBucket, e);
        }
    }
    
    /**
     * 查询并聚合指定分钟的数据
     */
    private List<AggregatedMetric> queryAndAggregate(String minuteBucket, LocalDateTime minute) throws SQLException {
        List<AggregatedMetric> results = new ArrayList<>();
        
        String sql = """
            SELECT
                exp_ids[1] AS exp_id,
                variants[1] AS variant,
                layers[1] AS layer,
                COUNT(*) AS total_events,
                uniqExact(user_id) AS total_users,
                sumIf(1, event_type = 'conversion') AS conversions,
                uniqExactIf(user_id, event_type = 'conversion') AS converted_users,
                sum(toFloat64OrDefault(JSONExtractString(properties, 'revenue'))) AS total_revenue
            FROM victor.events
            WHERE timestamp >= ? 
              AND timestamp < ?
              AND length(exp_ids) > 0
            GROUP BY exp_ids[1], variants[1], layers[1]
            """;
        
        try (Connection conn = dataSourceFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setTimestamp(1, Timestamp.valueOf(minute.withSecond(0).withNano(0)));
            ps.setTimestamp(2, Timestamp.valueOf(minute.withSecond(0).plusMinutes(1).withNano(0)));
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AggregatedMetric metric = buildMetric(rs, minuteBucket);
                    results.add(metric);
                }
            }
        }
        
        return results;
    }
    
    /**
     * 构建聚合指标对象
     */
    private AggregatedMetric buildMetric(ResultSet rs, String minuteBucket) throws SQLException {
        long totalUsers = rs.getLong("total_users");
        long convertedUsers = rs.getLong("converted_users");
        double conversionRate = totalUsers > 0 ? (double) convertedUsers / totalUsers : 0;
        double totalRevenue = rs.getDouble("total_revenue");
        double avgRevenuePerUser = totalUsers > 0 ? totalRevenue / totalUsers : 0;
        
        // Wilson score confidence interval
        double[] confidenceInterval = calculateWilsonCI(conversionRate, totalUsers, 0.95);
        
        // Significance score (lower bound of Wilson CI)
        double significanceScore = confidenceInterval[0];
        
        return AggregatedMetric.builder()
            .expId(rs.getString("exp_id"))
            .variant(rs.getString("variant"))
            .layer(rs.getString("layer"))
            .metricDate(minuteBucket.substring(0, 10))
            .minuteBucket(minuteBucket)
            .totalUsers(totalUsers)
            .convertedUsers(convertedUsers)
            .totalEvents(rs.getLong("total_events"))
            .conversions(rs.getLong("conversions"))
            .conversionRate(conversionRate)
            .totalRevenue(totalRevenue)
            .avgRevenuePerUser(avgRevenuePerUser)
            .confidenceIntervalLower(confidenceInterval[0])
            .confidenceIntervalUpper(confidenceInterval[1])
            .significanceScore(significanceScore)
            .build();
    }
    
    /**
     * 计算 Wilson 置信区间
     */
    private double[] calculateWilsonCI(double p, long n, double confidence) {
        if (n == 0) {
            return new double[]{0, 0};
        }
        
        // Z value for 95% confidence (1.96)
        double z = 1.96;
        
        double denominator = 1 + z * z / n;
        double center = p + z * z / (2 * n);
        double halfWidth = z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n));
        
        double lower = (center - halfWidth) / denominator;
        double upper = (center + halfWidth) / denominator;
        
        return new double[]{Math.max(0, lower), Math.min(1, upper)};
    }
    
    /**
     * 持久化聚合指标到 experiment_metrics 表
     */
    private void persistMetrics(List<AggregatedMetric> metrics) throws SQLException {
        String sql = """
            INSERT INTO victor.experiment_metrics 
            (metric_date, minute_bucket, exp_id, variant, layer, 
             total_events, unique_users, conversions, conversion_users,
             total_revenue, avg_revenue, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now64(3))
            """;
        
        try (Connection conn = dataSourceFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (AggregatedMetric metric : metrics) {
                ps.setDate(1, Date.valueOf(metric.getMetricDate()));
                ps.setString(2, metric.getMinuteBucket());
                ps.setString(3, metric.getExpId());
                ps.setString(4, metric.getVariant());
                ps.setString(5, metric.getLayer());
                ps.setLong(6, metric.getTotalEvents());
                ps.setLong(7, metric.getTotalUsers());
                ps.setLong(8, metric.getConversions());
                ps.setLong(9, metric.getConvertedUsers());
                ps.setDouble(10, metric.getTotalRevenue());
                ps.setDouble(11, metric.getAvgRevenuePerUser());
                ps.addBatch();
            }
            
            ps.executeBatch();
        }
    }
    
    /**
     * 日汇总聚合 (T+1)
     * 每天凌晨执行，聚合前一天的数据
     */
    public int aggregateDailyMetrics(LocalDate date) {
        log.info("Starting daily aggregation for date: {}", date);
        int experimentsProcessed = 0;
        
        try {
            // 获取所有实验 ID
            Set<String> experimentIds = getAllExperimentIds(date);
            log.info("Found {} experiments for date: {}", experimentIds.size(), date);
            
            for (String expId : experimentIds) {
                try {
                    aggregateDailyMetricsForExperiment(expId, date);
                    experimentsProcessed++;
                } catch (Exception e) {
                    log.error("Failed to aggregate daily metrics for experiment: {}", expId, e);
                }
            }
            
            log.info("Daily aggregation completed. Processed {} experiments", experimentsProcessed);
        } catch (Exception e) {
            log.error("Failed to aggregate daily metrics for date: {}", date, e);
        }
        
        return experimentsProcessed;
    }
    
    /**
     * 获取指定日期的所有实验 ID
     */
    private Set<String> getAllExperimentIds(LocalDate date) throws SQLException {
        Set<String> ids = new HashSet<>();
        
        String sql = """
            SELECT DISTINCT exp_ids[1] AS exp_id
            FROM victor.events
            WHERE toDate(timestamp) = ?
              AND length(exp_ids) > 0
            """;
        
        try (Connection conn = dataSourceFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String expId = rs.getString("exp_id");
                    if (expId != null && !expId.isEmpty()) {
                        ids.add(expId);
                    }
                }
            }
        }
        
        return ids;
    }
    
    /**
     * 聚合单个实验的日数据
     */
    private void aggregateDailyMetricsForExperiment(String expId, LocalDate date) throws SQLException {
        String sql = """
            SELECT
                exp_ids[1] AS exp_id,
                variants[1] AS variant,
                layers[1] AS layer,
                toDate(timestamp) AS metric_date,
                COUNT(*) AS total_events,
                uniqExact(user_id) AS total_users,
                sumIf(1, event_type = 'conversion') AS conversions,
                uniqExactIf(user_id, event_type = 'conversion') AS converted_users,
                sum(toFloat64OrDefault(JSONExtractString(properties, 'revenue'))) AS total_revenue
            FROM victor.events
            WHERE toDate(timestamp) = ?
              AND exp_ids[1] = ?
            GROUP BY exp_ids[1], variants[1], layers[1], toDate(timestamp)
            """;
        
        String insertSql = """
            INSERT INTO victor.experiment_daily_summary
            (metric_date, exp_id, variant, layer, total_events, unique_users,
             conversions, conversion_users, total_revenue, avg_revenue_per_user,
             conversion_rate, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now64(3))
            """;
        
        try (Connection conn = dataSourceFactory.getConnection();
             PreparedStatement selectPs = conn.prepareStatement(sql);
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            
            selectPs.setDate(1, Date.valueOf(date));
            selectPs.setString(2, expId);
            
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next()) {
                    long totalUsers = rs.getLong("total_users");
                    long convertedUsers = rs.getLong("converted_users");
                    double totalRevenue = rs.getDouble("total_revenue");
                    double conversionRate = totalUsers > 0 ? (double) convertedUsers / totalUsers : 0;
                    double avgRevenue = totalUsers > 0 ? totalRevenue / totalUsers : 0;
                    
                    insertPs.setDate(1, rs.getDate("metric_date"));
                    insertPs.setString(2, rs.getString("exp_id"));
                    insertPs.setString(3, rs.getString("variant"));
                    insertPs.setString(4, rs.getString("layer"));
                    insertPs.setLong(5, rs.getLong("total_events"));
                    insertPs.setLong(6, totalUsers);
                    insertPs.setLong(7, rs.getLong("conversions"));
                    insertPs.setLong(8, convertedUsers);
                    insertPs.setDouble(9, totalRevenue);
                    insertPs.setDouble(10, avgRevenue);
                    insertPs.setDouble(11, conversionRate);
                    insertPs.addBatch();
                }
                
                insertPs.executeBatch();
            }
        }
        
        log.debug("Aggregated daily metrics for experiment: {}, date: {}", expId, date);
    }
}
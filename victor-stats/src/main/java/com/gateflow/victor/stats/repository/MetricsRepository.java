package com.gateflow.victor.stats.repository;

import com.gateflow.victor.stats.config.StatsClickHouseConfig;
import com.gateflow.victor.stats.model.SampleStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * MetricsRepository - 从 ClickHouse 查询聚合指标
 * 提供实验/变体/时间范围的指标查询
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MetricsRepository {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final StatsClickHouseConfig config;
    
    /**
     * 查询实验变体的累计指标
     * 
     * @param expId 实验ID
     * @param variant 变体名称
     * @param layer 层名称
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 样本统计量（用户数、转化数）
     */
    public SampleStatistics queryVariantStats(
            String expId, 
            String variant, 
            String layer,
            LocalDate startDate,
            LocalDate endDate
    ) {
        String sql = """
            SELECT 
                sum(unique_users) AS total_users,
                sum(conversions) AS total_conversions,
                sum(total_revenue) AS total_revenue
            FROM victor.experiment_metrics
            WHERE exp_id = ?
              AND variant = ?
              AND layer = ?
              AND metric_date >= ?
              AND metric_date <= ?
            """;
        
        try (Connection conn = config.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, expId);
            ps.setString(2, variant);
            ps.setString(3, layer);
            ps.setDate(4, Date.valueOf(startDate));
            ps.setDate(5, Date.valueOf(endDate));
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long totalUsers = rs.getLong("total_users");
                    long totalConversions = rs.getLong("total_conversions");
                    double totalRevenue = rs.getDouble("total_revenue");
                    
                    // 计算转化率作为均值
                    double conversionRate = totalUsers > 0 ? (double) totalConversions / totalUsers : 0;
                    
                    return SampleStatistics.builder()
                        .n(totalUsers)
                        .mean(conversionRate)
                        .variance(conversionRate * (1 - conversionRate)) // 二项分布方差
                        .sum(totalConversions)
                        .build();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query variant stats for expId={}, variant={}", expId, variant, e);
        }
        
        // 返回空统计
        return SampleStatistics.builder()
            .n(0)
            .mean(0)
            .variance(0)
            .sum(0)
            .build();
    }
    
    /**
     * 查询实验所有变体的累计指标
     */
    public Map<String, VariantStats> queryExperimentStats(
            String expId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, VariantStats> results = new HashMap<>();
        
        String sql = """
            SELECT 
                variant,
                layer,
                sum(unique_users) AS total_users,
                sum(conversions) AS total_conversions,
                sum(total_events) AS total_events,
                sum(total_revenue) AS total_revenue
            FROM victor.experiment_metrics
            WHERE exp_id = ?
              AND metric_date >= ?
              AND metric_date <= ?
            GROUP BY variant, layer
            """;
        
        try (Connection conn = config.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, expId);
            ps.setDate(2, Date.valueOf(startDate));
            ps.setDate(3, Date.valueOf(endDate));
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VariantStats stats = new VariantStats();
                    stats.setVariant(rs.getString("variant"));
                    stats.setLayer(rs.getString("layer"));
                    stats.setTotalUsers(rs.getLong("total_users"));
                    stats.setTotalConversions(rs.getLong("total_conversions"));
                    stats.setTotalEvents(rs.getLong("total_events"));
                    stats.setTotalRevenue(rs.getDouble("total_revenue"));
                    
                    if (stats.getTotalUsers() > 0) {
                        stats.setConversionRate((double) stats.getTotalConversions() / stats.getTotalUsers());
                        stats.setAvgRevenue(stats.getTotalRevenue() / stats.getTotalUsers());
                    }
                    
                    results.put(stats.getVariant(), stats);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query experiment stats for expId={}", expId, e);
        }
        
        return results;
    }
    
    /**
     * 查询每日趋势数据
     */
    public Map<LocalDate, DailyStats> queryDailyTrend(
            String expId,
            String variant,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<LocalDate, DailyStats> results = new HashMap<>();
        
        String sql = """
            SELECT 
                metric_date,
                sum(unique_users) AS total_users,
                sum(conversions) AS total_conversions,
                sum(total_revenue) AS total_revenue
            FROM victor.experiment_metrics
            WHERE exp_id = ?
              AND variant = ?
              AND metric_date >= ?
              AND metric_date <= ?
            GROUP BY metric_date
            ORDER BY metric_date
            """;
        
        try (Connection conn = config.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, expId);
            ps.setString(2, variant);
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("metric_date").toLocalDate();
                    DailyStats stats = new DailyStats();
                    stats.setDate(date);
                    stats.setTotalUsers(rs.getLong("total_users"));
                    stats.setTotalConversions(rs.getLong("total_conversions"));
                    stats.setTotalRevenue(rs.getDouble("total_revenue"));
                    
                    if (stats.getTotalUsers() > 0) {
                        stats.setConversionRate((double) stats.getTotalConversions() / stats.getTotalUsers());
                    }
                    
                    results.put(date, stats);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query daily trend for expId={}, variant={}", expId, variant, e);
        }
        
        return results;
    }
    
    /**
     * 查询实时指标（最近 N 分钟）
     */
    public Map<String, VariantStats> queryRealtimeStats(String expId, int minutes) {
        Map<String, VariantStats> results = new HashMap<>();

        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        String sinceStr = since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String sql = """
            SELECT
                variants[1] AS variant,
                layers[1] AS layer,
                count() AS total_events,
                uniqExact(user_id) AS total_users,
                countIf(event_type = 'conversion') AS total_conversions,
                0 AS total_revenue
            FROM victor.events
            WHERE has(exp_ids, ?)
              AND timestamp >= ?
            GROUP BY variants[1], layers[1]
            """;

        try (Connection conn = config.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, expId);
            ps.setString(2, sinceStr);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VariantStats stats = new VariantStats();
                    stats.setVariant(rs.getString("variant"));
                    stats.setLayer(rs.getString("layer"));
                    stats.setTotalUsers(rs.getLong("total_users"));
                    stats.setTotalConversions(rs.getLong("total_conversions"));
                    stats.setTotalEvents(rs.getLong("total_events"));
                    stats.setTotalRevenue(rs.getDouble("total_revenue"));

                    if (stats.getTotalUsers() > 0) {
                        stats.setConversionRate((double) stats.getTotalConversions() / stats.getTotalUsers());
                    }

                    results.put(stats.getVariant(), stats);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query realtime stats for expId={}", expId, e);
        }

        return results;
    }
    
    /**
     * 变体统计内部类
     */
    @lombok.Data
    public static class VariantStats {
        private String variant;
        private String layer;
        private long totalUsers;
        private long totalConversions;
        private long totalEvents;
        private double totalRevenue;
        private double conversionRate;
        private double avgRevenue;
    }
    
    /**
     * 每日统计内部类
     */
    @lombok.Data
    public static class DailyStats {
        private LocalDate date;
        private long totalUsers;
        private long totalConversions;
        private double totalRevenue;
        private double conversionRate;
    }
}
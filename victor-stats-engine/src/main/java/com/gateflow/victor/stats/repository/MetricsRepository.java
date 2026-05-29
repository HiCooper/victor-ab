package com.gateflow.victor.stats.repository;

import com.gateflow.victor.stats.config.ClickHouseDataSourceConfig;
import com.gateflow.victor.stats.model.SampleStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MetricsRepository - 从 ClickHouse 查询聚合指标
 * 提供实验/变体/时间范围的指标查询
 */
@Slf4j
@Repository
public class MetricsRepository {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DataSource dataSource;

    public MetricsRepository(@Qualifier("clickhouseDataSource") ClickHouseDataSourceConfig.ClickHouseDataSourceFactory chFactory) {
        this.dataSource = chFactory.getDataSource();
    }
    
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
        
        try (Connection conn = dataSource.getConnection();
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
        
        try (Connection conn = dataSource.getConnection();
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
        
        try (Connection conn = dataSource.getConnection();
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
     * 查询实时指标（最近 N 分钟），从预聚合表读取。
     */
    public Map<String, VariantStats> queryRealtimeStats(String expId, int minutes) {
        Map<String, VariantStats> results = new HashMap<>();

        String sql = """
            SELECT
                variant,
                layer,
                sum(total_events)         AS total_events,
                sum(unique_users)         AS total_users,
                sum(conversions)          AS total_conversions,
                sum(total_revenue)        AS total_revenue
            FROM victor.experiment_metrics FINAL
            WHERE exp_id = ?
              AND minute_bucket >= now() - INTERVAL ? MINUTE
            GROUP BY variant, layer
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, expId);
            ps.setInt(2, minutes);

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
     * 查询实时趋势（分钟级时间序列），从预聚合表读取。
     */
    public List<Map<String, Object>> queryRealtimeTrend(String expId, int hours) {
        List<Map<String, Object>> rows = new ArrayList<>();

        String sql = """
            SELECT
                minute_bucket,
                variant,
                sum(unique_users)  AS users,
                sum(conversions)   AS conversions,
                sum(total_revenue) AS revenue
            FROM victor.experiment_metrics FINAL
            WHERE exp_id = ?
              AND minute_bucket >= now() - INTERVAL ? HOUR
            GROUP BY minute_bucket, variant
            ORDER BY minute_bucket
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, expId);
            ps.setInt(2, hours);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("minute_bucket", rs.getTimestamp("minute_bucket").toLocalDateTime().toString());
                    row.put("variant", rs.getString("variant"));
                    row.put("users", rs.getLong("users"));
                    row.put("conversions", rs.getLong("conversions"));
                    row.put("revenue", rs.getDouble("revenue"));
                    long users = rs.getLong("users");
                    double rate = users > 0 ? (double) rs.getLong("conversions") / users : 0;
                    row.put("conversionRate", Math.round(rate * 10000.0) / 10000.0);
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query realtime trend for expId={}", expId, e);
        }

        return rows;
    }

    /**
     * 查询事件明细
     */
    public List<Map<String, Object>> queryEventDetails(String expId, int limit, int offset) {
        List<Map<String, Object>> events = new ArrayList<>();

        String sql = """
            SELECT
                event_id,
                event_type,
                user_id,
                timestamp,
                platform,
                device_id,
                session_id,
                variants,
                layers,
                properties
            FROM victor.events
            WHERE has(exp_ids, ?)
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, expId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("eventId", rs.getString("event_id"));
                    event.put("eventType", rs.getString("event_type"));
                    event.put("userId", rs.getString("user_id"));
                    event.put("timestamp", rs.getTimestamp("timestamp").toString());
                    event.put("platform", rs.getString("platform"));
                    event.put("deviceId", rs.getString("device_id"));
                    event.put("sessionId", rs.getString("session_id"));
                    // Array columns - get first element or whole array
                    Array variantsArray = rs.getArray("variants");
                    event.put("variant", variantsArray != null ? variantsArray.getArray() : null);
                    Array layersArray = rs.getArray("layers");
                    event.put("layer", layersArray != null ? layersArray.getArray() : null);
                    event.put("properties", rs.getString("properties"));
                    events.add(event);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query event details for expId={}", expId, e);
        }

        return events;
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
     * 查询用户级数据（CUPED 方差缩减用）
     * 返回每用户的两个值：实验期转化状态 Y 和 实验前转化率 X
     *
     * @param expId 实验ID
     * @param variant 变体名称
     * @param startDate 实验开始日期
     * @param endDate 实验结束日期
     * @param preStartDate 实验前窗口开始日期
     * @param preEndDate 实验前窗口结束日期
     */
    public List<UserMetric> queryUserLevelData(
            String expId,
            String variant,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate preStartDate,
            LocalDate preEndDate
    ) {
        List<UserMetric> results = new ArrayList<>();

        // Step 1: 查询实验期每用户的转化状态
        String expSql = """
            SELECT user_id, converted, conversion_count
            FROM victor.user_experiment_stats FINAL
            WHERE exp_id = ?
              AND variant = ?
              AND stat_date >= ?
              AND stat_date <= ?
            """;

        Map<String, UserMetric> userMap = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(expSql)) {

            ps.setString(1, expId);
            ps.setString(2, variant);
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("user_id");
                    UserMetric m = new UserMetric();
                    m.userId = userId;
                    m.experimentValue = rs.getBoolean("converted") ? 1.0 : 0.0;
                    m.preExperimentValue = 0.0; // will be filled in step 2
                    userMap.put(userId, m);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query user-level experiment data for expId={}, variant={}", expId, variant, e);
            return results;
        }

        if (userMap.isEmpty()) {
            return results;
        }

        // Step 2: 查询实验前窗口同一批用户的历史转化率
        List<String> userIds = new ArrayList<>(userMap.keySet());
        String preSql = """
            SELECT user_id,
                   countIf(event_type = 'conversion') / count() AS pre_cvr
            FROM victor.events
            WHERE user_id IN (?""".replace("?", buildInClause(userIds.size()))
            + """
            )
              AND event_date >= ?
              AND event_date <= ?
            GROUP BY user_id
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(preSql)) {

            for (int i = 0; i < userIds.size(); i++) {
                ps.setString(i + 1, userIds.get(i));
            }
            ps.setDate(userIds.size() + 1, Date.valueOf(preStartDate));
            ps.setDate(userIds.size() + 2, Date.valueOf(preEndDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("user_id");
                    UserMetric m = userMap.get(userId);
                    if (m != null) {
                        m.preExperimentValue = rs.getDouble("pre_cvr");
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query pre-experiment data for expId={}, variant={}", expId, variant, e);
        }

        results.addAll(userMap.values());
        return results;
    }

    /**
     * Build a comma-separated ? placeholders for SQL IN clause.
     */
    private String buildInClause(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }

    /**
     * 用户级指标（CUPED 用）
     */
    @lombok.Data
    public static class UserMetric {
        private String userId;
        /** 实验期转化标记 (0 或 1) */
        private double experimentValue;
        /** 实验前转化率 (0.0 ~ 1.0) */
        private double preExperimentValue;
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
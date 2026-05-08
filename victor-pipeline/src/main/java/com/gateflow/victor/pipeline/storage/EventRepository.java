package com.gateflow.victor.pipeline.storage;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * EventRepository - ClickHouse事件存储
 * 支持批量插入以提升性能
 */
@Slf4j
@Repository
public class EventRepository {
    
    private static final String INSERT_SQL = """
        INSERT INTO victor.events (
            event_date, event_id, event_type, user_id, timestamp,
            platform, device_id, session_id, exp_ids, variants, layers,
            properties, received_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now64(3))
        """;
    
    private final ClickHouseConfig config;
    private final ObjectMapper objectMapper;
    
    private volatile ClickHouseDataSource dataSource;

    public EventRepository(ClickHouseConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取ClickHouse DataSource - 按需初始化
     */
    private ClickHouseDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    try {
                        dataSource = config.createDataSource();
                        log.info("ClickHouse DataSource initialized: {}", config.getUrl());
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to create ClickHouse DataSource", e);
                    }
                }
            }
        }
        return dataSource;
    }

    public void insert(EventDTO event) {
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            bindEventParameters(ps, event, conn);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to insert event to ClickHouse: {}", event.getEventId(), e);
            throw new RuntimeException("ClickHouse insert failed", e);
        }
    }
    
    /**
     * 批量插入事件 - 高性能版本
     * 使用事务批量提交，减少网络往返
     */
    public int insertBatch(List<EventDTO> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        try (Connection conn = getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                for (EventDTO event : events) {
                    try {
                        bindEventParameters(ps, event, conn);
                        ps.addBatch();
                        ps.clearParameters();
                    } catch (Exception e) {
                        log.error("Failed to bind event: {}", event.getEventId(), e);
                    }
                }
                
                int[] results = ps.executeBatch();
                conn.commit();
                
                for (int result : results) {
                    if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                        successCount++;
                    }
                }
                
                log.info("Batch inserted {} events to ClickHouse", successCount);
            } catch (SQLException e) {
                conn.rollback();
                log.error("Batch insert failed, rolled back", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to batch insert events", e);
            throw new RuntimeException("ClickHouse batch insert failed", e);
        }
        
        return successCount;
    }
    
    /**
     * 绑定事件参数到 PreparedStatement
     */
    private void bindEventParameters(PreparedStatement ps, EventDTO event, Connection conn) throws Exception {
        LocalDateTime timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(event.getTimestamp()), ZoneId.systemDefault()
        );
        
        ps.setDate(1, Date.valueOf(timestamp.toLocalDate()));
        ps.setString(2, event.getEventId());
        ps.setString(3, event.getEventType());
        ps.setString(4, event.getUserId());
        ps.setTimestamp(5, Timestamp.valueOf(timestamp));
        ps.setString(6, event.getPlatform());
        ps.setString(7, event.getDeviceId());
        ps.setString(8, event.getSessionId());
        
        if (event.getExperimentTags() != null && !event.getExperimentTags().isEmpty()) {
            ps.setArray(9, conn.createArrayOf("String",
                event.getExperimentTags().stream().map(t -> t.getExpId()).toArray()));
            ps.setArray(10, conn.createArrayOf("String",
                event.getExperimentTags().stream().map(t -> t.getVariant()).toArray()));
            ps.setArray(11, conn.createArrayOf("String",
                event.getExperimentTags().stream().map(t -> t.getLayer()).toArray()));
        } else {
            ps.setArray(9, conn.createArrayOf("String", new String[0]));
            ps.setArray(10, conn.createArrayOf("String", new String[0]));
            ps.setArray(11, conn.createArrayOf("String", new String[0]));
        }
        
        ps.setString(12, event.getProperties() != null ? 
            objectMapper.writeValueAsString(event.getProperties()) : "{}");
    }
}
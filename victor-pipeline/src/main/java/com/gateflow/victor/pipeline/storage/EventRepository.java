package com.gateflow.victor.pipeline.storage;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * EventRepository - ClickHouse事件存储
 * DataSource按需创建，不暴露为Spring Bean，避免Flyway发现
 */
@Slf4j
@Repository
public class EventRepository {
    
    private final ClickHouseConfig config;
    private final ObjectMapper objectMapper;
    
    // DataSource按需创建，不是Spring Bean
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
        String sql = """
            INSERT INTO victor.events (
                event_date, event_id, event_type, user_id, timestamp,
                platform, device_id, session_id, exp_ids, variants, layers,
                properties, received_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now64(3))
            """;
        
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getTimestamp()), ZoneId.systemDefault()
            );
            
            ps.setDate(1, java.sql.Date.valueOf(timestamp.toLocalDate()));
            ps.setString(2, event.getEventId());
            ps.setString(3, event.getEventType());
            ps.setString(4, event.getUserId());
            ps.setTimestamp(5, java.sql.Timestamp.valueOf(timestamp));
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
            
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to insert event to ClickHouse: {}", event.getEventId(), e);
            throw new RuntimeException("ClickHouse insert failed", e);
        }
    }
}
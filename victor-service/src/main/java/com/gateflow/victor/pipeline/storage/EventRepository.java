package com.gateflow.victor.pipeline.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.stats.config.ClickHouseDataSourceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * EventRepository - ClickHouse event storage.
 * Uses the shared {@code clickhouseDataSource} bean for batch inserts.
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

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public EventRepository(@Qualifier("clickhouseDataSource") ClickHouseDataSourceConfig.ClickHouseDataSourceFactory chFactory,
                           ObjectMapper objectMapper) {
        this.dataSource = chFactory.getDataSource();
        this.objectMapper = objectMapper;
    }

    public void insert(EventDTO event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            bindEventParameters(ps, event, conn);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to insert event to ClickHouse: {}", event.getEventId(), e);
            throw new RuntimeException("ClickHouse insert failed", e);
        }
    }

    public int insertBatch(List<EventDTO> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        try (Connection conn = dataSource.getConnection()) {
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

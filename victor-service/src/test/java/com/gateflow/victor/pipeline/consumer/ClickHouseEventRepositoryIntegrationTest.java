package com.gateflow.victor.pipeline.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.pipeline.storage.EventRepository;
import com.gateflow.victor.stats.config.ClickHouseDataSourceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数据管道「落库」段集成测试：ClickHouseWriter → EventRepository → 真实 ClickHouse。
 * <p>
 * 无 Docker 时整个类自动跳过；有 Docker（CI）时验证批量写入 victor.events 后可读回。
 */
@Testcontainers(disabledWithoutDocker = true)
class ClickHouseEventRepositoryIntegrationTest {

    @Container
    static final ClickHouseContainer CLICKHOUSE =
            new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:23.8"));

    private static DataSource dataSource;
    private static EventRepository repository;

    @BeforeAll
    static void initSchema() throws Exception {
        Properties props = new Properties();
        props.setProperty("user", CLICKHOUSE.getUsername());
        props.setProperty("password", CLICKHOUSE.getPassword());
        dataSource = new com.clickhouse.jdbc.ClickHouseDataSource(CLICKHOUSE.getJdbcUrl(), props);

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS victor");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS victor.events (
                        event_date  Date DEFAULT toDate(timestamp),
                        event_id    String,
                        event_type  String,
                        user_id     String,
                        timestamp   DateTime64(3),
                        platform    String,
                        device_id   String,
                        session_id  String,
                        exp_ids     Array(String),
                        buckets     Array(String),
                        layers      Array(String),
                        properties  String,
                        received_at DateTime64(3) DEFAULT now64(3)
                    ) ENGINE = MergeTree()
                    ORDER BY (event_date, user_id, timestamp)
                    """);
        }

        repository = new EventRepository(
                new ClickHouseDataSourceConfig.ClickHouseDataSourceFactory(dataSource),
                new ObjectMapper());
    }

    private EventDTO event(String id, String user) {
        EventDTO e = new EventDTO();
        e.setEventId(id);
        e.setUserId(user);
        e.setTimestamp(System.currentTimeMillis());
        e.setPlatform("web");
        e.setDeviceId("dev-" + user);
        e.setSessionId("sess-" + user);
        e.setProperties(Map.of("k", "v"));
        return e;
    }

    @Test
    @DisplayName("ClickHouseWriter 批量写入后可从 victor.events 读回")
    void writeBatchAndReadBack() throws Exception {
        ClickHouseWriter writer = new ClickHouseWriter(repository);
        writer.writeBatch(List.of(event("e1", "u1"), event("e2", "u2")));

        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count() FROM victor.events")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }
}

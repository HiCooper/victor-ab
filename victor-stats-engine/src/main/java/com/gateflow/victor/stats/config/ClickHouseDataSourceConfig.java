package com.gateflow.victor.stats.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Unified ClickHouse DataSource configuration.
 * Provides a ClickHouse-backed DataSource wrapped in a holder to prevent
 * Spring DataSource auto-detection and MyBatis-Plus from picking it up
 * instead of the MySQL primary DataSource.
 */
@Slf4j
@Configuration
public class ClickHouseDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "victor.clickhouse")
    public ClickHouseProperties clickHouseProperties() {
        return new ClickHouseProperties();
    }

    @Bean("clickhouseDataSource")
    public ClickHouseDataSourceFactory clickhouseDataSourceFactory(ClickHouseProperties props) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", props.getUser());
        p.setProperty("password", props.getPassword());
        p.setProperty("socket_timeout", "30000");
        p.setProperty("connection_timeout", "10000");
        p.setProperty("http_connection_provider", "HTTP_URL_CONNECTION");
        ClickHouseDataSource ds = new ClickHouseDataSource(props.getUrl(), p);
        log.info("ClickHouse DataSource initialized: {}", props.getUrl());
        return new ClickHouseDataSourceFactory(ds);
    }

    @Getter
    @RequiredArgsConstructor
    public static class ClickHouseDataSourceFactory {
        private final DataSource dataSource;
    }

    @Data
    public static class ClickHouseProperties {
        private String url;
        private String user;
        private String password;
    }
}
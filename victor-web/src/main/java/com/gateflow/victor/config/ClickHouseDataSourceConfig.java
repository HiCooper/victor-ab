package com.gateflow.victor.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import java.util.Properties;

/**
 * Unified ClickHouse DataSource configuration.
 * Provides a single shared DataSource bean for both pipeline and stats modules.
 * Uses {@code @Qualifier("clickhouseDataSource")} to avoid conflict with MySQL.
 * Flyway is restricted to {@code victor_experiment} schema, so it ignores this bean.
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
    public ClickHouseDataSource clickhouseDataSource(ClickHouseProperties props) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", props.getUser());
        p.setProperty("password", props.getPassword());
        p.setProperty("socket_timeout", "30000");
        p.setProperty("connection_timeout", "10000");
        ClickHouseDataSource ds = new ClickHouseDataSource(props.getUrl(), p);
        log.info("ClickHouse DataSource initialized: {}", props.getUrl());
        return ds;
    }

    @Data
    public static class ClickHouseProperties {
        private String url;
        private String user;
        private String password;
    }
}

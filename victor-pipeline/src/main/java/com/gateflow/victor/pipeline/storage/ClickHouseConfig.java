package com.gateflow.victor.pipeline.storage;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Properties;

/**
 * ClickHouse配置 - 仅存储配置属性，不暴露DataSource为Spring Bean
 * 这样Flyway不会发现ClickHouse DataSource并尝试迁移
 */
@Data
@Component
@ConfigurationProperties(prefix = "victor.clickhouse")
public class ClickHouseConfig {
    private String url;
    private String user;
    private String password;

    /**
     * 创建ClickHouse DataSource - 不作为Spring Bean
     * 由调用方按需创建，避免Flyway自动发现
     */
    public ClickHouseDataSource createDataSource() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("socket_timeout", "30000");
        props.setProperty("connection_timeout", "10000");
        return new ClickHouseDataSource(url, props);
    }
}
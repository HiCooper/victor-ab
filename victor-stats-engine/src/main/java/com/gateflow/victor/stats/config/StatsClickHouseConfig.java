package com.gateflow.victor.stats.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Stats Module ClickHouse Configuration
 * 用于统计模块查询 ClickHouse 数据
 */
@Data
@Component
@ConfigurationProperties(prefix = "victor.stats.clickhouse")
public class StatsClickHouseConfig {
    private String url;
    private String user;
    private String password;
    
    private volatile ClickHouseDataSource dataSource;
    
    /**
     * 获取 ClickHouse DataSource
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    Properties props = new Properties();
                    props.setProperty("user", user);
                    props.setProperty("password", password);
                    props.setProperty("socket_timeout", "30000");
                    props.setProperty("connection_timeout", "10000");
                    props.setProperty("max_execution_time", "120");
                    dataSource = new ClickHouseDataSource(url, props);
                }
            }
        }
        return dataSource.getConnection();
    }
}
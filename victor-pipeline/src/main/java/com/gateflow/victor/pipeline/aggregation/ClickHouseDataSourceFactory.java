package com.gateflow.victor.pipeline.aggregation;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.gateflow.victor.pipeline.storage.ClickHouseConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * ClickHouse DataSource Factory
 * 提供独立的 DataSource 实例用于聚合任务
 */
@Slf4j
@Component
public class ClickHouseDataSourceFactory {
    
    private final ClickHouseConfig config;
    private volatile ClickHouseDataSource dataSource;
    
    public ClickHouseDataSourceFactory(ClickHouseConfig config) {
        this.config = config;
    }
    
    /**
     * 获取 ClickHouse DataSource
     */
    public ClickHouseDataSource getDataSource() throws SQLException {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    dataSource = config.createDataSource();
                    log.info("ClickHouse DataSource initialized for aggregation");
                }
            }
        }
        return dataSource;
    }
    
    /**
     * 获取 ClickHouse Connection
     */
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }
}
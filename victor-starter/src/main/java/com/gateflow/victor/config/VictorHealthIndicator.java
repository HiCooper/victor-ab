package com.gateflow.victor.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Victor 应用健康检查
 * 验证关键依赖连通性：MySQL、Redis
 */
@Component("victor")
public class VictorHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public VictorHealthIndicator(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("service", "victor-ab");

        checkDatabase(builder);
        checkRedis(builder);

        return builder.build();
    }

    private void checkDatabase(Health.Builder builder) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
            builder.withDetail("database", "UP");
        } catch (Exception e) {
            builder.down()
                    .withDetail("database", "DOWN")
                    .withDetail("database_error", e.getMessage());
        }
    }

    private void checkRedis(Health.Builder builder) {
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            String pong = conn.ping();
            builder.withDetail("redis", "PONG".equals(pong) ? "UP" : "DOWN");
        } catch (Exception e) {
            builder.down()
                    .withDetail("redis", "DOWN")
                    .withDetail("redis_error", e.getMessage());
        }
    }
}

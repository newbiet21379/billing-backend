package com.acme.billing.monitoring.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;

/**
 * Health indicator for PostgreSQL database connectivity.
 *
 * Checks if the database is reachable and can execute simple queries.
 * Also provides database connection pool metrics if available.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
    private static final String VALIDATION_QUERY = "SELECT 1";
    private static final String METADATA_QUERY = """
        SELECT
            version() as version,
            current_database() as database,
            current_user as user,
            (SELECT count(*) FROM pg_stat_activity WHERE state = 'active') as active_connections
        """;

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(readOnly = true)
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            Instant start = Instant.now();

            // Test basic connectivity
            try (PreparedStatement stmt = connection.prepareStatement(VALIDATION_QUERY);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next() && rs.getInt(1) == 1) {
                    Duration queryTime = Duration.between(start, Instant.now());

                    // Gather database metadata
                    DatabaseMetadata metadata = gatherMetadata(connection);

                    return Health.up()
                        .withDetail("service", "PostgreSQL Database")
                        .withDetail("url", connection.getMetaData().getURL())
                        .withDetail("database", metadata.database())
                        .withDetail("user", metadata.user())
                        .withDetail("version", metadata.version())
                        .withDetail("activeConnections", metadata.activeConnections())
                        .withDetail("responseTime", queryTime.toMillis() + "ms")
                        .withDetail("autoCommit", connection.getAutoCommit())
                        .withDetail("readOnly", connection.isReadOnly())
                        .withDetail("transactionIsolation", getTransactionIsolation(connection))
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
                } else {
                    return Health.down()
                        .withDetail("service", "PostgreSQL Database")
                        .withDetail("error", "Validation query returned unexpected result")
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
                }
            }

        } catch (Exception e) {
            logger.warn("Database health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("service", "PostgreSQL Database")
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        }
    }

    /**
     * Gather database metadata for health reporting.
     */
    private DatabaseMetadata gatherMetadata(Connection connection) {
        try (PreparedStatement stmt = connection.prepareStatement(METADATA_QUERY);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new DatabaseMetadata(
                    rs.getString("version"),
                    rs.getString("database"),
                    rs.getString("user"),
                    rs.getInt("active_connections")
                );
            }
        } catch (Exception e) {
            logger.debug("Failed to gather database metadata: {}", e.getMessage());
        }

        return new DatabaseMetadata("Unknown", "Unknown", "Unknown", -1);
    }

    /**
     * Get human-readable transaction isolation level.
     */
    private String getTransactionIsolation(Connection connection) {
        try {
            int level = connection.getTransactionIsolation();
            return switch (level) {
                case Connection.TRANSACTION_NONE -> "NONE";
                case Connection.TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED";
                case Connection.TRANSACTION_READ_COMMITTED -> "READ_COMMITTED";
                case Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ";
                case Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE";
                default -> "UNKNOWN";
            };
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Record for database metadata.
     */
    private record DatabaseMetadata(
        String version,
        String database,
        String user,
        int activeConnections
    ) {}
}
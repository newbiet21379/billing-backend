package com.acme.billing.monitoring.database;

import com.acme.billing.metrics.BillProcessingMetrics;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hibernate statement inspector for tracking database query performance.
 *
 * This interceptor:
 * - Monitors SQL query execution times
 * - Identifies slow queries and problematic patterns
 * - Tracks query complexity and frequency
 * - Provides query performance metrics
 * - Logs warnings for slow or inefficient queries
 */
@Component
public class QueryPerformanceInterceptor implements StatementInspector {

    private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceInterceptor.class);
    private static final long SLOW_QUERY_THRESHOLD_MS = 500; // 500ms
    private static final long VERY_SLOW_QUERY_THRESHOLD_MS = 2000; // 2 seconds

    private final BillProcessingMetrics metrics;
    private final ThreadLocal<Long> queryStartTime = new ThreadLocal<>();

    public QueryPerformanceInterceptor(BillProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public String inspect(String sql) {
        long startTime = System.currentTimeMillis();
        queryStartTime.set(startTime);

        // Log query start for debugging (only in debug mode)
        if (logger.isDebugEnabled()) {
            logger.debug("Executing SQL query: {}", normalizeSql(sql));
        }

        return sql; // Return the SQL unchanged
    }

    /**
     * Called after query execution to record performance metrics.
     * This method should be called by a Hibernate post-execute listener.
     */
    public void recordQueryExecution(String sql, long rowCount) {
        Long startTime = queryStartTime.get();
        if (startTime == null) {
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        queryStartTime.remove();

        try {
            // Record metrics
            recordQueryMetrics(sql, duration, rowCount);

            // Log slow queries
            logSlowQueries(sql, duration, rowCount);

            // Log problematic query patterns
            analyzeQueryPatterns(sql, duration);

        } catch (Exception e) {
            logger.debug("Error recording query performance: {}", e.getMessage(), e);
        } finally {
            queryStartTime.remove();
        }
    }

    /**
     * Records query performance metrics.
     */
    private void recordQueryMetrics(String sql, long duration, long rowCount) {
        String queryType = determineQueryType(sql);
        String tableName = extractMainTableName(sql);

        // Record query timing
        BillProcessingMetrics.Timer.Sample timerSample = metrics.startDatabaseQueryTimer(queryType);
        metrics.recordDatabaseQueryTime(timerSample, queryType);

        // Record additional metrics for complex operations
        if (duration > SLOW_QUERY_THRESHOLD_MS) {
            metrics.recordFailure("slow_queries");
        } else {
            metrics.recordSuccess("slow_queries");
        }

        // Log query details for analysis
        if (duration > VERY_SLOW_QUERY_THRESHOLD_MS) {
            logger.warn("Very slow query detected - type: {}, table: {}, duration: {}ms, rows: {}, sql: {}",
                queryType, tableName, duration, rowCount, normalizeSql(sql.substring(0, Math.min(sql.length(), 200))));
        }
    }

    /**
     * Logs warnings for slow queries.
     */
    private void logSlowQueries(String sql, long duration, long rowCount) {
        if (duration > VERY_SLOW_QUERY_THRESHOLD_MS) {
            logger.warn("SLOW QUERY - correlation_id: {}, duration: {}ms, rows: {}, sql: {}",
                "N/A", duration, rowCount, normalizeSql(sql));
        } else if (duration > SLOW_QUERY_THRESHOLD_MS) {
            logger.info("Slow query - correlation_id: {}, duration: {}ms, rows: {}, sql: {}",
                "N/A", duration, rowCount, normalizeSql(sql));
        }
    }

    /**
     * Analyzes query patterns for potential issues.
     */
    private void analyzeQueryPatterns(String sql, long duration) {
        String upperSql = sql.toUpperCase();

        // Check for potentially problematic patterns
        if (upperSql.contains("SELECT *") && duration > 100) {
            logger.warn("Potential SELECT * performance issue - consider specifying columns, duration: {}ms", duration);
        }

        if (upperSql.contains("LIKE '%") || upperSql.contains("LIKE '%")) {
            logger.warn("Leading wildcard LIKE detected - may cause full table scan, duration: {}ms", duration);
        }

        if (upperSql.contains("ORDER BY") && !upperSql.contains("LIMIT") && !upperSql.contains("TOP")) {
            logger.info("ORDER BY without LIMIT detected - consider adding pagination, duration: {}ms", duration);
        }

        if (upperSql.contains("SELECT DISTINCT") && duration > SLOW_QUERY_THRESHOLD_MS) {
            logger.info("Slow DISTINCT query - consider indexing, duration: {}ms", duration);
        }

        if (upperSql.contains("GROUP BY") && duration > SLOW_QUERY_THRESHOLD_MS) {
            logger.info("Slow GROUP BY query - consider indexing grouping columns, duration: {}ms", duration);
        }

        if (upperSql.contains("JOIN") && duration > SLOW_QUERY_THRESHOLD_MS) {
            logger.info("Slow JOIN query - check join conditions and indexes, duration: {}ms", duration);
        }

        if (upperSql.contains("SUBQUERY") || (upperSql.contains("(") && upperSql.contains("SELECT") &&
            sql.split("(?i)select").length > 2)) {
            logger.info("Complex subquery detected - consider optimization, duration: {}ms", duration);
        }
    }

    /**
     * Determines the type of SQL operation.
     */
    private String determineQueryType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "unknown";
        }

        String trimmedSql = sql.trim().toUpperCase();

        if (trimmedSql.startsWith("SELECT")) {
            if (trimmedSql.contains("INSERT INTO") || trimmedSql.contains("INSERT")) {
                return "insert_select";
            }
            return "select";
        } else if (trimmedSql.startsWith("INSERT")) {
            return "insert";
        } else if (trimmedSql.startsWith("UPDATE")) {
            return "update";
        } else if (trimmedSql.startsWith("DELETE")) {
            return "delete";
        } else if (trimmedSql.startsWith("CREATE") || trimmedSql.startsWith("ALTER") || trimmedSql.startsWith("DROP")) {
            return "ddl";
        } else if (trimmedSql.startsWith("BEGIN") || trimmedSql.startsWith("COMMIT") || trimmedSql.startsWith("ROLLBACK")) {
            return "transaction";
        } else {
            return "other";
        }
    }

    /**
     * Extracts the main table name from the SQL query.
     */
    private String extractMainTableName(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "unknown";
        }

        String upperSql = sql.toUpperCase();

        try {
            if (upperSql.startsWith("SELECT")) {
                // Extract FROM clause table
                int fromIndex = upperSql.indexOf(" FROM ");
                if (fromIndex != -1) {
                    String afterFrom = sql.substring(fromIndex + 6);
                    String[] parts = afterFrom.split("[\\s,;]");
                    if (parts.length > 0) {
                        String tableName = parts[0].trim();
                        // Remove schema prefix if present
                        if (tableName.contains(".")) {
                            tableName = tableName.substring(tableName.indexOf('.') + 1);
                        }
                        return tableName;
                    }
                }
            } else if (upperSql.startsWith("INSERT")) {
                // Extract INTO clause table
                int intoIndex = upperSql.indexOf(" INTO ");
                if (intoIndex != -1) {
                    String afterInto = sql.substring(intoIndex + 6);
                    String[] parts = afterInto.split("[\\s,;(]");
                    if (parts.length > 0) {
                        return parts[0].trim();
                    }
                }
            } else if (upperSql.startsWith("UPDATE")) {
                // Extract table name after UPDATE
                int updateIndex = upperSql.indexOf("UPDATE ");
                if (updateIndex != -1) {
                    String afterUpdate = sql.substring(updateIndex + 7);
                    String[] parts = afterUpdate.split("[\\s,;]");
                    if (parts.length > 0) {
                        return parts[0].trim();
                    }
                }
            } else if (upperSql.startsWith("DELETE")) {
                // Extract table name from DELETE FROM
                int fromIndex = upperSql.indexOf(" FROM ");
                if (fromIndex != -1) {
                    String afterFrom = sql.substring(fromIndex + 6);
                    String[] parts = afterFrom.split("[\\s,;]");
                    if (parts.length > 0) {
                        return parts[0].trim();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting table name from SQL: {}", e.getMessage());
        }

        return "unknown";
    }

    /**
     * Normalizes SQL for logging by removing excessive whitespace and limiting length.
     */
    private String normalizeSql(String sql) {
        if (sql == null) {
            return null;
        }

        // Remove extra whitespace
        String normalized = sql.replaceAll("\\s+", " ").trim();

        // Limit length for readability
        if (normalized.length() > 300) {
            normalized = normalized.substring(0, 297) + "...";
        }

        return normalized;
    }

    /**
     * Gets query performance statistics summary.
     */
    public QueryPerformanceStats getPerformanceStats() {
        // This would typically be implemented with actual tracking data
        return new QueryPerformanceStats(0, 0, 0, 0.0);
    }

    /**
     * Record for query performance statistics.
     */
    public record QueryPerformanceStats(
        long totalQueries,
        long slowQueries,
        long verySlowQueries,
        double averageDuration
    ) {}
}
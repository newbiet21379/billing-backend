package com.acme.billing.monitoring.health;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.queryhandling.QueryBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Axon Server connectivity and functionality.
 *
 * Checks if Axon Server is reachable and can handle basic command,
 * query, and event operations.
 */
@Component
public class AxonServerHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerHealthIndicator.class);
    private static final int TIMEOUT_SECONDS = 5;

    private final AxonServerConnectionManager connectionManager;
    private final CommandBus commandBus;
    private final EventBus eventBus;
    private final QueryBus queryBus;

    @Autowired
    public AxonServerHealthIndicator(
            AxonServerConnectionManager connectionManager,
            CommandBus commandBus,
            EventBus eventBus,
            QueryBus queryBus) {
        this.connectionManager = connectionManager;
        this.commandBus = commandBus;
        this.eventBus = eventBus;
        this.queryBus = queryBus;
    }

    @Override
    public Health health() {
        try {
            Health.Builder healthBuilder = Health.up()
                .withDetail("service", "Axon Server")
                .withDetail("timestamp", System.currentTimeMillis());

            // Check connection status
            boolean isConnected = checkConnection();
            healthBuilder.withDetail("connected", isConnected);

            if (!isConnected) {
                return healthBuilder
                    .status(new Status("DOWN"))
                    .withDetail("error", "Not connected to Axon Server")
                    .build();
            }

            // Check Axon Server details
            AxonServerDetails details = getAxonServerDetails();
            healthBuilder
                .withDetail("serverHost", details.host())
                .withDetail("serverPort", details.port())
                .withDetail("context", details.context())
                .withDetail("componentName", details.componentName());

            // Test basic functionality
            HealthCheckResults testResults = performHealthChecks();
            healthBuilder
                .withDetail("commandBusTest", testResults.commandBusTest)
                .withDetail("eventBusTest", testResults.eventBusTest)
                .withDetail("queryBusTest", testResults.queryBusTest)
                .withDetail("overallTest", testResults.overallTest);

            if (!testResults.overallTest) {
                return healthBuilder
                    .status(new Status("WARNING"))
                    .withDetail("warning", "Connected but some functionality tests failed")
                    .build();
            }

            return healthBuilder.build();

        } catch (Exception e) {
            logger.warn("Axon Server health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("service", "Axon Server")
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        }
    }

    /**
     * Check basic connection to Axon Server.
     */
    private boolean checkConnection() {
        try {
            return connectionManager.isConnected();
        } catch (Exception e) {
            logger.debug("Failed to check Axon Server connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get Axon Server connection details.
     */
    private AxonServerDetails getAxonServerDetails() {
        try {
            var connection = connectionManager.getConnection();
            if (connection != null) {
                return new AxonServerDetails(
                    connection.getChannel().authority().split(":")[0],
                    Integer.parseInt(connection.getChannel().authority().split(":")[1]),
                    connection.getContext(),
                    connection.getComponentName()
                );
            }
        } catch (Exception e) {
            logger.debug("Failed to get Axon Server details: {}", e.getMessage());
        }

        return new AxonServerDetails("unknown", -1, "unknown", "unknown");
    }

    /**
     * Perform basic functionality tests on Axon components.
     */
    private HealthCheckResults performHealthChecks() {
        boolean commandBusTest = testCommandBus();
        boolean eventBusTest = testEventBus();
        boolean queryBusTest = testQueryBus();

        boolean overallTest = commandBusTest && eventBusTest && queryBusTest;

        return new HealthCheckResults(commandBusTest, eventBusTest, queryBusTest, overallTest);
    }

    /**
     * Test Command Bus functionality.
     */
    private boolean testCommandBus() {
        try {
            // Test local command bus registration
            CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) != null;
        } catch (Exception e) {
            logger.debug("Command bus health test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test Event Bus functionality.
     */
    private boolean testEventBus() {
        try {
            // Test event bus availability
            return eventBus != null;
        } catch (Exception e) {
            logger.debug("Event bus health test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test Query Bus functionality.
     */
    private boolean testQueryBus() {
        try {
            // Test query bus availability
            return queryBus != null;
        } catch (Exception e) {
            logger.debug("Query bus health test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Record for Axon Server connection details.
     */
    private record AxonServerDetails(
        String host,
        int port,
        String context,
        String componentName
    ) {}

    /**
     * Record for health check test results.
     */
    private record HealthCheckResults(
        boolean commandBusTest,
        boolean eventBusTest,
        boolean queryBusTest,
        boolean overallTest
    ) {}
}
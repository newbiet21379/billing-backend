package com.acme.billing.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.actuator.autoconfigure.health.HealthProperties;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.info.BuildProperties;

/**
 * Configuration class for Spring Boot Actuator and observability.
 *
 * This configures:
 * - Actuator endpoints exposure and security
 * - Metrics collection and export
 * - Health indicators
 * - Application information
 * - Distributed tracing
 */
@Configuration
public class ActuatorConfig {

    /**
     * Customize meter registry with common tags for application identification.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(BuildProperties buildProperties) {
        return registry -> registry
            .config()
            .commonTags(
                "application", "billing-backend",
                "version", buildProperties.getVersion(),
                "java.version", System.getProperty("java.version")
            );
    }

    /**
     * Business metrics binder for bill processing workflows.
     */
    @Bean
    public MeterBinder billProcessingMetrics() {
        return new BillProcessingMetrics();
    }

    /**
     * Custom info contributor for application metadata.
     */
    @Bean
    public InfoContributor applicationInfo(BuildProperties buildProperties) {
        return builder -> {
            builder.withDetail("application", "Billing & Expense Processing Service");
            builder.withDetail("version", buildProperties.getVersion());
            builder.withDetail("buildTime", buildProperties.getTime());
            builder.withDetail("java", System.getProperty("java.version"));
            builder.withDetail("spring-boot", buildProperties.getSpringBootVersion());
            builder.withDetail("description", "CQRS-based bill processing with OCR integration");
        };
    }
}
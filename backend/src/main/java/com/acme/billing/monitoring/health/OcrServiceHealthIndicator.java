package com.acme.billing.monitoring.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Health indicator for OCR service availability.
 *
 * Checks if the OCR service is reachable and responding correctly.
 * Uses the /health endpoint of the OCR service if available,
 * otherwise performs a simple connectivity check.
 */
@Component
public class OcrServiceHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(OcrServiceHealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String HEALTH_ENDPOINT = "/health";

    private final WebClient webClient;
    private final String ocrServiceUrl;

    public OcrServiceHealthIndicator(
            @Value("${ocr.service.url}") String ocrServiceUrl) {
        this.ocrServiceUrl = ocrServiceUrl;
        this.webClient = WebClient.builder()
            .baseUrl(ocrServiceUrl)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
            .build();
    }

    @Override
    public Health health() {
        try {
            logger.debug("Checking OCR service health at {}", ocrServiceUrl);

            // Try to call the OCR service health endpoint
            String healthResponse = webClient.get()
                .uri(HEALTH_ENDPOINT)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .onErrorResume(WebClientResponseException.NotFound.class,
                    e -> Mono.just("Health endpoint not found, checking service availability"))
                .onErrorResume(e -> {
                    logger.warn("OCR service health check failed: {}", e.getMessage());
                    return Mono.error(e);
                })
                .block();

            return Health.up()
                .withDetail("service", "OCR Service")
                .withDetail("url", ocrServiceUrl)
                .withDetail("response", healthResponse)
                .withDetail("timestamp", System.currentTimeMillis())
                .build();

        } catch (Exception e) {
            logger.warn("OCR service is not available: {}", e.getMessage());
            return Health.down()
                .withDetail("service", "OCR Service")
                .withDetail("url", ocrServiceUrl)
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        }
    }

    /**
     * Additional connectivity check method that can be called manually.
     */
    public boolean isServiceAvailable() {
        return health().getStatus().getCode().equals("UP");
    }
}
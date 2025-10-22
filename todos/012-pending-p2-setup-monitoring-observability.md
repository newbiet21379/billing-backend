---
status: pending
priority: p2
issue_id: "012"
tags: [monitoring, observability, actuator, health-checks, logging]
dependencies: ["002", "007", "008"]
---

# Setup Monitoring, Health Checks, and Observability

## Problem Statement
Implement comprehensive monitoring with Spring Boot Actuator, custom metrics, health checks, and logging. This provides visibility into system health, performance, and operational issues across all services.

## Findings
- No visibility into service health status
- Cannot monitor performance or detect issues
- No metrics for business operations
- Troubleshooting production issues difficult
- Capacity planning impossible without data
- Location: Multiple files (actuator config, custom health indicators)

## Proposed Solutions

### Option 1: Implement comprehensive observability stack
- **Pros**: Enables proactive monitoring, performance optimization, and operational excellence
- **Cons**: Requires configuration and metric design effort
- **Effort**: Medium (4-5 hours)
- **Risk**: Low (Spring Boot Actuator well-established)

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/main/java/com/acme/billing/config/ActuatorConfig.java`
  - `backend/src/main/java/com/acme/billing/monitoring/` (custom indicators)
  - `backend/src/main/resources/application.yml` (actuator config)
  - `backend/src/main/resources/logback-spring.xml` (logging config)
- **Related Components**: All services, external dependencies
- **Database Changes**: No

## Monitoring Components:

### 1. Spring Boot Actuator Endpoints:
- **/actuator/health**: Service health status
- **/actuator/metrics**: JVM and application metrics
- **/actuator/info**: Application information
- **/actuator/prometheus**: Prometheus metrics export
- **/actuator/env**: Environment configuration

### 2. Custom Health Indicators:
```java
@Component
public class OcrServiceHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check OCR service availability
        return Health.up()
            .withDetail("service", "OCR Service")
            .withDetail("endpoint", "http://ocr:7070/health")
            .build();
    }
}

@Component
public class MinioHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check MinIO connectivity
        return Health.up()
            .withDetail("storage", "MinIO")
            .withDetail("bucket", "bills")
            .build();
    }
}
```

### 3. Business Metrics:
- **Bill Processing Success Rate**: OCR and approval workflows
- **File Upload Performance**: Size, processing time
- **API Response Times**: By endpoint and percentile
- **Database Query Performance**: Slow query detection
- **Event Processing Metrics**: Command/throughput rates

### 4. Structured Logging:
```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="json" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <mdc/>
                <arguments/>
                <stackTrace/>
                <pattern>
                    <pattern>{"correlationId": "%X{correlationId:-NA}"}</pattern>
                </pattern>
            </providers>
        </encoder>
    </appender>
</configuration>
```

## Configuration Required:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  endpoint:
    health:
      show-details: always
      show-components: always
  metrics:
    export:
      prometheus:
        enabled: true
  health:
    ocr-service:
      enabled: true
    minio:
      enabled: true

logging:
  pattern:
    correlation-id: "%X{correlationId:-NA}"
  level:
    com.acme.billing: DEBUG
    org.axonframework: INFO
```

## Resources
- Original finding: GitHub issue triage
- Related issues: #002 (Docker Compose), #007 (REST Controllers), #008 (Projections)
- Spring Boot Actuator: https://spring.io/guides/gs/actuator-service/
- Micrometer Metrics: https://micrometer.io/
- Logstash Logging: https://github.com/logstash/logstash-logback-encoder

## Acceptance Criteria
- [ ] Spring Boot Actuator endpoints configured and secured
- [ ] Custom health indicators for OCR service and MinIO
- [ ] Business metrics for bill processing workflows
- [ ] Structured JSON logging with correlation IDs
- [ ] Performance monitoring for API endpoints
- [ ] Database query performance tracking
- [ ] Prometheus metrics export enabled
- [ ] Application info endpoint with version/build info
- [ ] Health check monitoring setup
- [ ] Log aggregation and analysis capabilities

## Monitoring Dashboard Example:
- **Service Health**: Up/down status for all components
- **Request Metrics**: RPS, response times, error rates
- **Business Metrics**: Bills processed per hour, OCR success rate
- **Infrastructure**: JVM memory, CPU usage, disk space
- **External Dependencies**: OCR service, MinIO connectivity

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P2 (IMPORTANT)
- Estimated effort: Medium (4-5 hours)

**Learnings:**
- Observability is essential for production operations and troubleshooting
- Custom health indicators provide service-specific status information
- Business metrics enable monitoring of system effectiveness beyond technical health
- Structured logging enables effective log analysis and correlation

## Notes
Source: Triage session on 2025-01-22
Dependencies: Should be implemented after core services (#002, #007, #008) are functional
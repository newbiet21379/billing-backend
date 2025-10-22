# Monitoring, Health Checks, and Observability Guide

## Overview

This document provides comprehensive information about the monitoring, health checks, and observability features implemented in the Billing & Expense Processing Service.

## 🏗️ Architecture

The observability stack includes:

- **Spring Boot Actuator**: Core metrics, health checks, and management endpoints
- **Micrometer**: Metrics collection and export to Prometheus
- **Custom Health Indicators**: Service-specific health checks
- **Structured Logging**: JSON logging with correlation IDs
- **Performance Monitoring**: Request timing and database query tracking
- **Business Metrics**: Domain-specific KPIs for bill processing workflows

## 📊 Actuator Endpoints

### Health Endpoints

| Endpoint | Description | Details |
|----------|-------------|---------|
| `/actuator/health` | Application health status | Overall health with component breakdown |
| `/actuator/health/liveness` | Liveness probe | Container/startup health |
| `/actuator/health/readiness` | Readiness probe | Service availability for traffic |
| `/actuator/health/db` | Database connectivity | PostgreSQL connection status |
| `/actuator/health/ocr` | OCR service health | External OCR service availability |
| `/actuator/health/minio` | Object storage health | MinIO connectivity and operations |
| `/actuator/health/axon` | Event store health | Axon Server connection and functionality |

### Metrics Endpoints

| Endpoint | Description | Format |
|----------|-------------|--------|
| `/actuator/metrics` | Available metrics | JSON list |
| `/actuator/metrics/{metric}` | Specific metric details | JSON with values |
| `/actuator/prometheus` | Prometheus export | Prometheus format |

### Management Endpoints

| Endpoint | Description | Security |
|----------|-------------|----------|
| `/actuator/info` | Application information | Public |
| `/actuator/env` | Environment properties | Restricted |
| `/actuator/loggers` | Logging configuration | Restricted |
| `/actuator/httptrace` | HTTP request traces | Restricted |
| `/actuator/threaddump` | Thread dump | Restricted |
| `/actuator/heapdump` | Heap dump | Restricted |

## 🔧 Configuration

### Actuator Configuration (application.yml)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,prometheus,httptrace,loggers,threaddump,heapdump
      base-path: /actuator
    cors:
      allowed-origins: "*"
      allowed-methods: GET,POST
  endpoint:
    health:
      show-details: always
      show-components: always
      group:
        liveness:
          include: "livenessState,diskSpace,ping"
        readiness:
          include: "readinessState,db,ocr,minio,axon"
  metrics:
    export:
      prometheus:
        enabled: true
        step: 60s
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
      sla:
        http.server.requests: 100ms, 200ms, 500ms, 1s, 2s
  tracing:
    sampling:
      probability: 0.1
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ACTUATOR_SECURITY_ENABLED` | Enable actuator security | `false` |
| `METRICS_EXPORT_PROMETHEUS_ENABLED` | Enable Prometheus export | `true` |
| `LOGGING_LEVEL_COM_ACME_BILLING` | Application log level | `INFO` |
| `TRACING_SAMPLING_PROBABILITY` | Distributed tracing sample rate | `0.1` |

## 🏥 Custom Health Indicators

### OCR Service Health Indicator

**Location**: `com.acme.billing.monitoring.health.OcrServiceHealthIndicator`

**Features**:
- Checks OCR service connectivity via `/health` endpoint
- Monitors response times and availability
- Falls back to basic connectivity check

**Health Details**:
```json
{
  "status": "UP",
  "components": {
    "ocr": {
      "status": "UP",
      "details": {
        "service": "OCR Service",
        "url": "http://localhost:7070",
        "response": "OK",
        "timestamp": 1691234567890
      }
    }
  }
}
```

### MinIO Health Indicator

**Location**: `com.acme.billing.monitoring.health.MinioHealthIndicator`

**Features**:
- Verifies MinIO connectivity and authentication
- Tests bucket operations (list, upload, delete)
- Monitors storage health and availability

**Health Details**:
```json
{
  "status": "UP",
  "components": {
    "minio": {
      "status": "UP",
      "details": {
        "service": "MinIO Object Storage",
        "bucketName": "billing-documents",
        "bucketExists": true,
        "totalBuckets": 5,
        "writeTest": true,
        "timestamp": 1691234567890
      }
    }
  }
}
```

### Database Health Indicator

**Location**: `com.acme.billing.monitoring.health.DatabaseHealthIndicator`

**Features**:
- PostgreSQL connectivity checks
- Query validation with performance timing
- Database metadata collection
- Connection pool monitoring

**Health Details**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "service": "PostgreSQL Database",
        "url": "jdbc:postgresql://localhost:5432/billing",
        "database": "billing",
        "user": "billing_user",
        "version": "PostgreSQL 14.5",
        "activeConnections": 8,
        "responseTime": "15ms",
        "autoCommit": true,
        "readOnly": false,
        "transactionIsolation": "READ_COMMITTED"
      }
    }
  }
}
```

### Axon Server Health Indicator

**Location**: `com.acme.billing.monitoring.health.AxonServerHealthIndicator`

**Features**:
- Axon Server connectivity monitoring
- Command, query, and event bus functionality testing
- Context and component information collection

**Health Details**:
```json
{
  "status": "UP",
  "components": {
    "axon": {
      "status": "UP",
      "details": {
        "service": "Axon Server",
        "connected": true,
        "serverHost": "localhost",
        "serverPort": 8124,
        "context": "billing-backend",
        "componentName": "billing-backend",
        "commandBusTest": true,
        "eventBusTest": true,
        "queryBusTest": true,
        "overallTest": true
      }
    }
  }
}
```

## 📈 Business Metrics

### Bill Processing Metrics

**Location**: `com.acme.billing.metrics.BillProcessingMetrics`

#### Counter Metrics

| Metric Name | Description | Tags |
|-------------|-------------|------|
| `bills.created.total` | Total bills created | - |
| `bills.approved.total` | Total bills approved | - |
| `files.attached.total` | Files attached to bills | `type` (pdf, jpeg, png, etc.) |
| `ocr.processing.total` | OCR processing attempts | - |
| `ocr.processing.success` | Successful OCR processing | - |
| `ocr.processing.failure` | Failed OCR processing | `error_type`, `file_type` |
| `api.requests.total` | API request count | `endpoint`, `method`, `status` |
| `axon.commands.processed.total` | Commands processed | `command_type`, `status` |
| `axon.events.processed.total` | Events processed | `event_type` |
| `axon.queries.processed.total` | Queries processed | `query_type` |

#### Timer Metrics

| Metric Name | Description | Tags |
|-------------|-------------|------|
| `bills.creation.duration` | Bill creation time | - |
| `files.upload.duration` | File upload time | `file_type` |
| `ocr.processing.duration` | OCR processing time | `file_type` |
| `bills.approval.duration` | Bill approval time | - |
| `api.requests.duration` | API request processing | `endpoint`, `method` |
| `database.query.duration` | Database query time | `query_type` |

#### Gauge Metrics

| Metric Name | Description | Unit |
|-------------|-------------|------|
| `bills.active.count` | Currently active bills | count |
| `bills.pending_approval.count` | Bills pending approval | count |
| `storage.usage.bytes` | Current storage usage | bytes |

#### Distribution Summaries

| Metric Name | Description | Tags |
|-------------|-------------|------|
| `files.size.bytes` | File size distribution | `file_type` |
| `ocr.processing.time.seconds` | OCR processing time distribution | `file_type` |

## 🔍 Structured Logging

### Log Configuration

**Location**: `src/main/resources/logback-spring.xml`

**Features**:
- JSON structured logging with Logstash encoder
- Correlation ID tracking for request tracing
- Separate log files for different log levels
- Async logging for improved performance
- Integration with Micrometer tracing

### Log Structure

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.acme.billing.service.BillService",
  "message": "Bill created successfully",
  "application": "billing-backend",
  "version": "1.0.0",
  "environment": "development",
  "correlation_id": "a1b2c3d4e5f6g7h8",
  "trace_id": "trace-123456",
  "span_id": "span-789012",
  "thread": "http-nio-8080-exec-1",
  "caller_data": {
    "class": "BillService",
    "method": "createBill",
    "line": 45
  }
}
```

### Log Categories

| Category | Purpose | Level |
|----------|---------|-------|
| `com.acme.billing` | Application logging | DEBUG/INFO |
| `REQUEST_LOGGER` | Request lifecycle logging | INFO |
| `org.axonframework` | Axon framework logging | INFO |
| `org.hibernate.SQL` | SQL query logging | DEBUG |
| `org.hibernate.type.descriptor.sql.BasicBinder` | SQL parameter logging | TRACE |
| `org.springframework.web.reactive.function.client` | HTTP client logging | DEBUG |

### Correlation ID Tracking

**Filter**: `com.acme.billing.monitoring.logging.CorrelationIdFilter`

**Features**:
- Automatic correlation ID generation and propagation
- Support for common tracing headers (X-Correlation-ID, X-Request-ID, Traceparent)
- Integration with distributed tracing systems
- Request/response header injection

## ⚡ Performance Monitoring

### Request Performance Interceptor

**Location**: `com.acme.billing.monitoring.interceptor.PerformanceMonitoringInterceptor`

**Features**:
- Request duration tracking
- Slow request detection and alerting
- Request size and response time metrics
- Exception tracking and error rate monitoring
- Client IP and User-Agent logging

**Slow Request Thresholds**:
- Slow request: > 1 second
- Very slow request: > 5 seconds

### Database Query Performance

**Interceptor**: `com.acme.billing.monitoring.database.QueryPerformanceInterceptor`

**Features**:
- SQL query execution time tracking
- Slow query detection (> 500ms threshold)
- Query pattern analysis and optimization suggestions
- Entity operation monitoring
- Hibernate event listener integration

**Query Analysis Features**:
- SELECT * usage detection
- Leading wildcard LIKE pattern detection
- Missing pagination in ORDER BY queries
- DISTINCT and GROUP BY performance monitoring
- JOIN operation tracking
- Subquery complexity analysis

## 🚀 Setup and Integration

### Local Development

1. **Start the application**:
   ```bash
   docker-compose up
   ```

2. **Access Actuator endpoints**:
   ```bash
   # Health check
   curl http://localhost:8080/actuator/health

   # Metrics list
   curl http://localhost:8080/actuator/metrics

   # Prometheus metrics
   curl http://localhost:8080/actuator/prometheus
   ```

3. **Monitor with correlation ID**:
   ```bash
   # Include correlation ID header
   curl -H "X-Correlation-ID: test-123" \
        http://localhost:8080/api/commands/bills
   ```

### Prometheus Integration

1. **Configure Prometheus** (prometheus.yml):
   ```yaml
   scrape_configs:
     - job_name: 'billing-backend'
       static_configs:
         - targets: ['localhost:8080']
       metrics_path: '/actuator/prometheus'
       scrape_interval: 60s
   ```

2. **Grafana Dashboard**:
   - Import the provided Grafana dashboard template
   - Key panels: API response times, error rates, business metrics
   - Alert rules: High error rate, slow response times, service unavailability

### Distributed Tracing

**Zipkin Integration**:
```yaml
management:
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of requests
```

**Supported Tracing Headers**:
- W3C Trace Context (`traceparent`)
- AWS X-Ray (`X-Amzn-Trace-Id`)
- Jaeger (`uber-trace-id`)
- Custom correlation headers

### Log Aggregation

**ELK Stack Integration**:
```json
{
  "input": {
    "file": {
      "path": "/var/log/billing-backend/*.json"
    }
  },
  "filter": {
    "json": {}
  },
  "output": {
    "elasticsearch": {
      "hosts": ["localhost:9200"],
      "index": "billing-backend-%{+YYYY.MM.dd}"
    }
  }
}
```

**Fluentd Integration**:
```xml
<source>
  @type tail
  path /var/log/billing-backend/*.json
  pos_file /var/log/fluentd/billing-backend.log.pos
  tag billing.backend
  format json
</source>
```

## 🔔 Alerting and Notifications

### Recommended Alert Rules

#### High-Level Service Health
```yaml
groups:
  - name: billing-backend-health
    rules:
      - alert: ServiceDown
        expr: up{job="billing-backend"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Billing backend service is down"

      - alert: DatabaseDown
        expr: health_status{component="db"} != 1
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "Database connectivity lost"

      - alert: OCRServiceDown
        expr: health_status{component="ocr"} != 1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "OCR service unavailable"
```

#### Performance Alerts
```yaml
  - name: billing-backend-performance
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"

      - alert: SlowResponseTime
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "95th percentile response time too high"

      - alert: SlowDatabaseQueries
        expr: database_query_duration_seconds_max > 1
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Slow database queries detected"
```

#### Business Metrics Alerts
```yaml
  - name: billing-backend-business
    rules:
      - alert: OCRFailureRate
        expr: rate(ocr_processing_failure_total[5m]) / rate(ocr_processing_total[5m]) > 0.3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High OCR processing failure rate"

      - alert: FileUploadFailures
        expr: rate(files_attached_total{status="failed"}[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "File upload failures increasing"
```

## 📋 Monitoring Checklist

### Pre-Production

- [ ] Actuator endpoints configured and secured
- [ ] Prometheus metrics integration verified
- [ ] Health indicators responding correctly
- [ ] Structured logging working with correlation IDs
- [ ] Performance monitoring enabled
- [ ] Database query tracking configured
- [ ] Error handling and alerting rules tested
- [ ] Log rotation and retention policies set

### Production

- [ ] Monitoring dashboards created and shared
- [ ] Alert notifications configured (Slack, email, PagerDuty)
- [ ] Log aggregation pipeline active
- [ ] Distributed tracing enabled for critical paths
- [ ] Regular monitoring review process established
- [ ] Capacity planning metrics defined
- [ ] Backup and disaster recovery monitoring

### Ongoing

- [ ] Monitor system performance trends
- [ ] Review and optimize slow queries
- [ ] Update alert thresholds based on usage patterns
- [ ] Regular monitoring tools maintenance
- [ ] Training team on monitoring tools and alert handling

## 🔧 Troubleshooting

### Common Issues

**Health Checks Failing**:
1. Verify external service availability
2. Check network connectivity and DNS
3. Review authentication credentials
4. Examine service logs for detailed errors

**High Memory Usage**:
1. Review heap dump (`/actuator/heapdump`)
2. Analyze thread dump (`/actuator/threaddump`)
3. Check for memory leaks in application metrics
4. Verify garbage collection patterns

**Slow Database Performance**:
1. Review slow query logs
2. Check connection pool configuration
3. Analyze query execution plans
4. Verify database index usage

**Missing Metrics**:
1. Verify Micrometer configuration
2. Check Prometheus endpoint accessibility
3. Review metric registration in code
4. Confirm metric collection intervals

### Debug Commands

```bash
# Health check details
curl http://localhost:8080/actuator/health

# Specific component health
curl http://localhost:8080/actuator/health/db

# Available metrics
curl http://localhost:8080/actuator/metrics

# Specific metric data
curl http://localhost:8080/actuator/metrics/http.server.requests

# Environment properties
curl http://localhost:8080/actuator/env

# Recent HTTP traces
curl http://localhost:8080/actuator/httptrace

# Change log level (POST)
curl -X POST -H "Content-Type: application/json" \
     -d '{"configuredLevel": "DEBUG"}' \
     http://localhost:8080/actuator/loggers/com.acme.billing
```

## 📚 Additional Resources

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/best-practices/)
- [Java Application Performance Monitoring](https://elastic.co/what-is/application-performance-monitoring/apm-for-java)

## 🆘 Support

For monitoring-related issues:

1. Check application logs for error messages
2. Verify configuration in application.yml
3. Review this troubleshooting guide
4. Check relevant component-specific documentation
5. Contact the platform operations team for infrastructure issues

---

*Last updated: October 2024*
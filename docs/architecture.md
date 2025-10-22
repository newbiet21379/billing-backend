# Architecture Documentation

## Overview

The Billing & Expense Processing Service is built using **CQRS (Command Query Responsibility Segregation)** and **Event Sourcing** patterns with the **Axon Framework**. This architecture provides excellent scalability, auditability, and maintainability for complex business domains.

## Core Architectural Patterns

### CQRS (Command Query Responsibility Segregation)

CQRS separates the read and write operations of a data store into different models:

- **Command Side**: Handles write operations (commands) and emits events
- **Query Side**: Handles read operations (queries) from optimized read models

```mermaid
graph LR
    subgraph "Write Model"
        CMD[Commands] --> AGG[Aggregates]
        AGG --> EVENTS[Events]
    end

    subgraph "Read Model"
        PROJECTIONS[Projections] --> READ_DB[(Read DB)]
        QUERIES[Queries] --> READ_DB
    end

    EVENTS --> PROJECTIONS

    CLIENT[Client] --> CMD
    CLIENT --> QUERIES
```

### Event Sourcing

Instead of storing the current state of entities, we store the sequence of events that led to that state:

- **Immutable Events**: All state changes are captured as immutable events
- **Event Store**: Events are stored in a time-ordered sequence
- **State Rebuilding**: Current state can be rebuilt by replaying events
- **Complete Audit Trail**: Every change is preserved with full context

```mermaid
sequenceDiagram
    participant Client
    participant CommandHandler
    participant Aggregate
    participant EventStore
    participant EventBus

    Client->>CommandHandler: Send Command
    CommandHandler->>Aggregate: Load from Events
    Aggregate->>Aggregate: Process Command
    Aggregate->>EventStore: Store Events
    EventStore->>EventBus: Publish Events
    EventBus->>ProjectionHandler: Update Projections
```

## System Architecture

### High-Level Architecture

```mermaid
graph TB
    subgraph "External Systems"
        WEB[Web Frontend]
        MOBILE[Mobile App]
        EXT_API[External APIs]
    end

    subgraph "API Gateway"
        GATEWAY[Spring Boot Gateway]
    end

    subgraph "Application Layer"
        COMMAND_API[Command API]
        QUERY_API[Query API]
        STORAGE_API[Storage API]
    end

    subgraph "Domain Layer"
        BILL_AGG[Bill Aggregate]
        FILE_AGG[File Aggregate]
        DOMAIN_EVENTS[Domain Events]
    end

    subgraph "Infrastructure Layer"
        AXON_SERVER[(Axon Server)]
        EVENT_STORE[(Event Store)]
        READ_DB[(PostgreSQL)]
        MINIO[(MinIO)]
        OCR_SERVICE[OCR Service]
        EMAIL_SERVICE[Email Service]
    end

    WEB --> GATEWAY
    MOBILE --> GATEWAY
    EXT_API --> GATEWAY

    GATEWAY --> COMMAND_API
    GATEWAY --> QUERY_API
    GATEWAY --> STORAGE_API

    COMMAND_API --> BILL_AGG
    COMMAND_API --> FILE_AGG
    BILL_AGG --> DOMAIN_EVENTS
    FILE_AGG --> DOMAIN_EVENTS

    DOMAIN_EVENTS --> AXON_SERVER
    AXON_SERVER --> EVENT_STORE
    AXON_SERVER --> READ_DB
    AXON_SERVER --> OCR_SERVICE
    AXON_SERVER --> EMAIL_SERVICE

    QUERY_API --> READ_DB
    STORAGE_API --> MINIO
```

### Service Interactions

```mermaid
sequenceDiagram
    participant User
    participant API
    participant CommandHandler
    participant BillAggregate
    participant AxonServer
    participant OcrService
    participant MinIO
    participant EmailService
    participant QueryHandler
    participant ReadDB

    User->>API: Create Bill Command
    API->>CommandHandler: CreateBillCommand
    CommandHandler->>BillAggregate: Create Bill
    BillAggregate->>AxonServer: BillCreatedEvent
    AxonServer->>QueryHandler: Update Projections
    QueryHandler->>ReadDB: Store Bill Summary

    User->>API: Upload File
    API->>MinIO: Store File
    API->>CommandHandler: AttachFileCommand
    CommandHandler->>BillAggregate: Attach File
    BillAggregate->>AxonServer: FileAttachedEvent
    AxonServer->>OcrService: Trigger OCR
    OcrService->>MinIO: Retrieve File
    OcrService->>AxonServer: OcrCompletedEvent
    AxonServer->>EmailService: Send Notification
    AxonServer->>ReadDB: Update Bill Details

    User->>API: Query Bills
    API->>QueryHandler: GetBillsQuery
    QueryHandler->>ReadDB: Fetch Bills
    ReadDB->>API: Return Bill List
    API->>User: Bills with OCR Data
```

## Domain Model

### Bill Aggregate

The **Bill Aggregate** is the core domain entity that manages bill lifecycle:

```mermaid
stateDiagram-v2
    [*] --> DRAFT: CreateBillCommand
    DRAFT --> PENDING: AttachFileCommand
    PENDING --> PROCESSING: OcrStartedEvent
    PROCESSING --> COMPLETED: OcrCompletedEvent
    PROCESSING --> FAILED: OcrFailedEvent
    COMPLETED --> APPROVED: ApproveBillCommand
    COMPLETED --> REJECTED: RejectBillCommand
    APPROVED --> PAID: MarkPaidCommand
    REJECTED --> DRAFT: UpdateBillCommand
    FAILED --> PENDING: RetryOcrCommand
```

### Events

The system emits the following domain events:

- **BillCreatedEvent**: New bill created
- **BillUpdatedEvent**: Bill information updated
- **FileAttachedEvent**: File attached to bill
- **OcrStartedEvent**: OCR processing initiated
- **OcrCompletedEvent**: OCR processing completed successfully
- **OcrFailedEvent**: OCR processing failed
- **BillApprovedEvent**: Bill approved
- **BillRejectedEvent**: Bill rejected
- **BillDeletedEvent**: Bill deleted

### Commands

- **CreateBillCommand**: Create a new bill
- **UpdateBillCommand**: Update bill information
- **AttachFileCommand**: Attach file to bill
- **ApplyOcrResultCommand**: Apply OCR processing results
- **ApproveBillCommand**: Approve bill for payment
- **RejectBillCommand**: Reject bill
- **DeleteBillCommand**: Delete bill

## Data Flow

### Command Processing Flow

```mermaid
flowchart TD
    START([Client Request]) --> VALIDATE[Validate Command]
    VALIDATE --> LOAD[Load Aggregate]
    LOAD --> PROCESS[Process Command]
    PROCESS --> EVENTS[Emit Events]
    EVENTS --> STORE[Store in Event Store]
    STORE --> PUBLISH[Publish to Event Bus]
    PUBLISH --> RESPONSE[Return Response]

    VALIDATE --> ERROR[Return Validation Error]
    PROCESS --> ERROR
```

### Query Processing Flow

```mermaid
flowchart TD
    START([Query Request]) --> FETCH[Fetch from Read Model]
    FETCH --> FILTER[Apply Filters]
    FILTER --> PROJECT[Project Results]
    PROJECT --> RESPONSE[Return Query Results]
```

### Event Processing Flow

```mermaid
flowchart TD
    EVENT([Domain Event]) --> HANDLERS[Event Handlers]
    HANDLERS --> PROJECTIONS[Update Projections]
    HANDLERS --> NOTIFICATIONS[Send Notifications]
    HANDLERS --> INTEGRATION[Integration Events]

    PROJECTIONS --> READ_DB[(Read Database)]
    NOTIFICATIONS --> EMAIL[Email Service]
    INTEGRATION --> EXTERNAL[External Systems]
```

## Infrastructure Components

### Axon Server

**Axon Server** serves as the central nervous system:

- **Event Store**: Immutable storage of all domain events
- **Command Bus**: Routes commands to appropriate handlers
- **Event Bus**: Distributes events to all subscribers
- **Query Bus**: Routes queries to query handlers

### PostgreSQL (Read Model)

Optimized for fast queries with:

- **Denormalized Data**: Pre-joined and optimized for read operations
- **Indexes**: Strategic indexes for common query patterns
- **Materialized Views**: Complex queries pre-computed for performance

### MinIO Object Storage

S3-compatible file storage for:

- **Bill Files**: Original uploaded documents
- **Processed Files**: Enhanced/processed versions
- **Temporary Files**: Files during processing

### OCR Service

Microservice for text extraction:

- **FastAPI**: Modern Python web framework
- **Tesseract**: Open-source OCR engine
- **Async Processing**: Non-blocking file processing
- **Error Handling**: Comprehensive error management

## Scalability Patterns

### Horizontal Scaling

- **Command Processing**: Scale command handlers independently
- **Query Processing**: Scale read models based on query load
- **Event Processing**: Parallel event handler processing
- **OCR Processing**: Scale OCR service based on file volume

### Event Sourcing Benefits

- **Event Replay**: Rebuild projections from scratch
- **Temporal Queries**: Query system state at any point in time
- **Debugging**: Replay events to debug issues
- **Audit Trail**: Complete history of all changes

### CQRS Benefits

- **Optimized Models**: Separate models for commands and queries
- **Performance**: Read models optimized for specific queries
- **Scalability**: Scale read and write independently
- **Flexibility**: Evolve read models without affecting writes

## Security Architecture

### Authentication & Authorization

```mermaid
graph TB
    CLIENT[Client] --> GATEWAY[API Gateway]
    GATEWAY --> AUTH[Authentication Service]
    AUTH --> TOKEN[JWT Token]
    TOKEN --> PERMISSIONS[Permission Check]
    PERMISSIONS --> COMMANDS[Command Authorization]
    PERMISSIONS --> QUERIES[Query Authorization]

    COMMANDS --> AUTHORIZED[Authorized Commands]
    QUERIES --> FILTERED[Filtered Results]
```

### Data Protection

- **Encryption**: Data encrypted at rest and in transit
- **Access Control**: Role-based access to commands and queries
- **Audit Logging**: All actions logged with user context
- **Data Isolation**: Multi-tenant data separation

## Monitoring & Observability

### Metrics Collection

```mermaid
graph LR
    SERVICES[Microservices] --> METRICS[Metrics Collection]
    METRICS --> PROMETHEUS[Prometheus]
    PROMETHEUS --> GRAFANA[Grafana Dashboards]
    PROMETHEUS --> ALERTS[Alerting Rules]

    SERVICES --> LOGS[Structured Logs]
    LOGS --> ELK[ELK Stack]

    SERVICES --> TRACES[Distributed Traces]
    TRACES --> JAEGER[Jaeger]
```

### Health Monitoring

- **Service Health**: Individual service health checks
- **Dependency Health**: External service availability
- **Database Health**: Database connectivity and performance
- **Event Store Health**: Axon Server status

### Performance Monitoring

- **Command Latency**: Time to process commands
- **Query Performance**: Database query performance
- **Event Processing**: Event handler throughput
- **Resource Usage**: Memory, CPU, and network usage

## Deployment Architecture

### Container Strategy

```mermaid
graph TB
    subgraph "Development"
        DEV_DB[(Dev DB)]
        DEV_MINIO[(Dev MinIO)]
        DEV_AXON[(Dev Axon)]
        DEV_BACKEND[Backend Dev]
        DEV_OCR[OCR Dev]
    end

    subgraph "Staging"
        STAGING_DB[(Staging DB)]
        STAGING_MINIO[(Staging MinIO)]
        STAGING_AXON[(Staging Axon)]
        STAGING_BACKEND[Backend Staging]
        STAGING_OCR[OCR Staging]
    end

    subgraph "Production"
        PROD_DB[(Production DB)]
        PROD_MINIO[(Production MinIO)]
        PROD_AXON[(Production Axon)]
        PROD_BACKEND[Backend Prod]
        PROD_OCR[OCR Prod]
    end
```

### Kubernetes Deployment

- **Pods**: Individual service instances
- **Services**: Service discovery and load balancing
- **ConfigMaps**: Configuration management
- **Secrets**: Sensitive data management
- **PersistentVolumes**: Durable storage

### CI/CD Pipeline

```mermaid
graph LR
    CODE[Code Commit] --> BUILD[Build & Test]
    BUILD --> SECURITY[Security Scan]
    SECURITY --> DEPLOY[Deploy to Staging]
    DEPLOY --> INTEGRATION[Integration Tests]
    INTEGRATION --> APPROVAL[Manual Approval]
    APPROVAL --> PROD[Deploy to Production]
```

## Architecture Decision Records

### ADR-001: Use CQRS with Event Sourcing

**Decision**: Implement CQRS with Event Sourcing using Axon Framework

**Rationale**:
- Complex business domain with evolving requirements
- Need for complete audit trail
- Scalability requirements for read/write operations
- Business rules require event-driven processing

**Alternatives Considered**:
- Traditional CRUD with relational database
- Event-driven architecture without event sourcing
- Microservices with shared database

### ADR-002: Separate OCR Service

**Decision**: Extract OCR functionality into separate microservice

**Rationale**:
- Different technology stack (Python vs Java)
- Independent scaling requirements
- Potential for multiple OCR engines
- Clear bounded context

### ADR-003: Use MinIO for Object Storage

**Decision**: Use MinIO instead of cloud storage

**Rationale**:
- S3-compatible API for future migration
- Self-hosted for data control
- Docker-native deployment
- Cost-effective for development/staging

## Future Architecture Evolution

### Event-Driven Architecture

- **Event Streaming**: Use Kafka for high-volume events
- **Event Mesh**: Service mesh for event routing
- **Event Catalog**: Centralized event documentation

### Microservices Evolution

- **Bounded Contexts**: Further domain decomposition
- **API Gateway**: Enhanced gateway functionality
- **Service Discovery**: Dynamic service registration

### Data Architecture

- **Read Model Optimization**: Multiple specialized read models
- **Caching Strategy**: Multi-level caching
- **Data Lake**: Historical data analysis

### Integration Patterns

- **Message Brokers**: Advanced message routing
- **API Composition**: GraphQL for flexible queries
- **Webhooks**: Real-time event notifications
# Development Guide

This guide provides comprehensive instructions for setting up a development environment, understanding the codebase, and contributing to the Billing & Expense Processing Service.

## Prerequisites

### Required Software

- **Java 21+** - OpenJDK or Oracle JDK
- **Maven 3.8+** - Build tool and dependency management
- **Docker & Docker Compose** - Container platform for services
- **Git** - Version control
- **IDE** - IntelliJ IDEA (recommended) or VS Code with Java extensions

### Recommended Tools

- **Postman** - API testing and collection management
- **DBeaver** - Database client for PostgreSQL
- **MongoDB Compass** - MongoDB client (if using for development)
- **Axon Dashboard** - Event monitoring and debugging

## Environment Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd billing-backend
```

### 2. Set Up Development Environment

```bash
# Copy environment configuration
cp .env.example .env

# Edit environment variables
nano .env
```

### 3. Start Infrastructure Services

```bash
# Start only infrastructure services (no application code)
docker compose up -d postgres axonserver minio mailhog

# Wait for services to be ready
docker compose logs -f postgres
```

### 4. Start OCR Service

```bash
# Build and start OCR service
docker compose up -d ocr

# Verify OCR service is running
curl http://localhost:7070/health
```

### 5. Run Backend Locally

```bash
cd backend

# Run the Spring Boot application
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 6. Verify Setup

```bash
# Check backend health
curl http://localhost:8080/actuator/health

# Check API documentation
open http://localhost:8080/swagger-ui.html
```

## Development Workflow

### 1. Feature Development

```bash
# Create feature branch
git checkout -b feature/bill-validation

# Make changes
# Add tests
# Run tests
mvn clean verify

# Format code
mvn spring-javaformat:apply

# Commit changes
git add .
git commit -m "feat: add bill validation logic"

# Push to fork
git push origin feature/bill-validation
```

### 2. Testing Strategy

```bash
# Unit tests only
mvn test

# Integration tests (requires Docker)
mvn verify -P integration-tests

# Full test suite
mvn clean verify

# Generate test coverage report
mvn jacoco:report
open target/site/jacoco/index.html
```

### 3. Code Quality

```bash
# Code formatting
mvn spring-javaformat:apply

# Check code formatting
mvn spring-javaformat:validate

# Static analysis
mvn sonar:sonar

# Dependency updates
mvn versions:display-dependency-updates

# Security scan
mvn org.owasp:dependency-check-maven:check
```

## Project Structure Deep Dive

### Backend Module Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/acme/billing/
│   │   │   ├── api/                    # Public API definitions
│   │   │   │   ├── commands/          # Command DTOs
│   │   │   │   │   ├── CreateBillCommand.java
│   │   │   │   │   ├── UpdateBillCommand.java
│   │   │   │   │   ├── AttachFileCommand.java
│   │   │   │   │   └── ApproveBillCommand.java
│   │   │   │   ├── queries/           # Query DTOs
│   │   │   │   │   ├── GetBillQuery.java
│   │   │   │   │   ├── ListBillsQuery.java
│   │   │   │   │   └── SearchBillsQuery.java
│   │   │   │   └── events/            # Domain events
│   │   │   │       ├── BillCreatedEvent.java
│   │   │   │       ├── BillUpdatedEvent.java
│   │   │   │       ├── FileAttachedEvent.java
│   │   │   │       └── BillApprovedEvent.java
│   │   │   ├── domain/                # Domain layer (business logic)
│   │   │   │   ├── model/             # Domain models
│   │   │   │   │   ├── Bill.java
│   │   │   │   │   ├── BillId.java
│   │   │   │   │   ├── FileAttachment.java
│   │   │   │   │   └── BillStatus.java
│   │   │   │   ├── aggregate/         # Aggregates
│   │   │   │   │   ├── BillAggregate.java
│   │   │   │   │   └── FileAggregate.java
│   │   │   │   ├── repository/        # Repository interfaces
│   │   │   │   │   ├── BillRepository.java
│   │   │   │   │   └── EventSourcedBillRepository.java
│   │   │   │   └── service/           # Domain services
│   │   │   │       ├── BillValidationService.java
│   │   │   │       └── OcrProcessingService.java
│   │   │   ├── application/           # Application layer
│   │   │   │   ├── command/           # Command handlers
│   │   │   │   │   ├── BillCommandHandler.java
│   │   │   │   │   └── FileCommandHandler.java
│   │   │   │   ├── query/             # Query handlers
│   │   │   │   │   ├── BillQueryHandler.java
│   │   │   │   │   └── FileQueryHandler.java
│   │   │   │   └── service/           # Application services
│   │   │   │       ├── OcrService.java
│   │   │   │       ├── StorageService.java
│   │   │   │       └── NotificationService.java
│   │   │   ├── infrastructure/         # Infrastructure layer
│   │   │   │   ├── persistence/       # Database implementations
│   │   │   │   │   ├── projection/    # JPA projections
│   │   │   │   │   │   ├── BillProjection.java
│   │   │   │   │   │   ├── BillSummary.java
│   │   │   │   │   │   └── FileMetadataProjection.java
│   │   │   │   │   └── repository/    # JPA repositories
│   │   │   │   │       ├── BillJpaRepository.java
│   │   │   │   │       └── FileMetadataJpaRepository.java
│   │   │   │   ├── messaging/         # Event handling
│   │   │   │   │   ├── OcrEventHandler.java
│   │   │   │   │   ├── NotificationEventHandler.java
│   │   │   │   │   └── ProjectionEventHandler.java
│   │   │   │   ├── external/          # External service clients
│   │   │   │   │   ├── OcrClient.java
│   │   │   │   │   ├── MinIOClient.java
│   │   │   │   │   └── EmailClient.java
│   │   │   │   └── config/            # Configuration
│   │   │   │       ├── AxonConfig.java
│   │   │   │       ├── DatabaseConfig.java
│   │   │   │       ├── StorageConfig.java
│   │   │   │       └── SecurityConfig.java
│   │   │   └── web/                   # Web layer
│   │   │       ├── rest/              # REST controllers
│   │   │       │   ├── BillCommandController.java
│   │   │       │   ├── BillQueryController.java
│   │   │       │   └── StorageController.java
│   │   │       ├── dto/               # Request/Response DTOs
│   │   │       │   ├── BillCreateRequest.java
│   │   │       │   ├── BillResponse.java
│   │   │       │   └── ErrorResponse.java
│   │   │       └── exception/        # Exception handling
│   │   │           ├── GlobalExceptionHandler.java
│   │   │           ├── BillNotFoundException.java
│   │   │           └── FileProcessingException.java
│   │   └── test/
│   │       ├── unit/                  # Unit tests
│   │       │   ├── domain/
│   │       │   ├── application/
│   │       │   └── infrastructure/
│   │       ├── integration/           # Integration tests
│   │       │   ├── api/
│   │       │   ├── database/
│   │       │   └── external/
│   │       └── e2e/                   # End-to-end tests
│   │           └── BillWorkflowE2ETest.java
│   └── resources/
│       ├── application.yml            # Main configuration
│       ├── application-dev.yml         # Development profile
│       ├── application-test.yml        # Test profile
│       └── application-prod.yml        # Production profile
├── pom.xml                            # Maven configuration
├── Dockerfile                        # Docker build configuration
└── README.md                          # Backend-specific documentation
```

### Key Components Explained

#### API Layer (`api/`)

Contains the public API contracts:
- **Commands**: DTOs for write operations
- **Queries**: DTOs for read operations
- **Events**: Domain event definitions

#### Domain Layer (`domain/`)

Pure business logic without infrastructure dependencies:
- **Aggregates**: Consistency boundaries and business rules
- **Domain Services**: Complex business operations
- **Repository Interfaces**: Domain-specific repository contracts

#### Application Layer (`application/`)

Coordinates between domain and infrastructure:
- **Command Handlers**: Process incoming commands
- **Query Handlers**: Process read queries
- **Application Services**: Complex use case orchestration

#### Infrastructure Layer (`infrastructure/`)

Technical implementations:
- **Persistence**: Database and projection implementations
- **Messaging**: Event handling and external service integration
- **External**: Third-party service clients

#### Web Layer (`web/`)

HTTP API exposure:
- **REST Controllers**: HTTP request handling
- **DTOs**: HTTP request/response objects
- **Exception Handling**: Global error handling

## Coding Standards

### Java Code Style

Follow Google Java Style Guide:

```java
/**
 * Bill aggregate responsible for managing bill lifecycle.
 *
 * <p>This aggregate enforces business rules for bill creation,
 * file attachment, and approval workflows.</p>
 */
@Aggregate
public class BillAggregate {

    @AggregateIdentifier
    private BillId billId;

    private String title;

    private BigDecimal total;

    private BillStatus status;

    // Aggregate must have a no-arg constructor for Axon
    protected BillAggregate() {
        // Required by Axon Framework
    }

    /**
     * Creates a new bill.
     *
     * @param command the create bill command
     */
    @CommandHandler
    public BillAggregate(CreateBillCommand command) {
        Objects.requireNonNull(command.getTitle(), "Title is required");
        Objects.requireNonNull(command.getTotal(), "Total is required");

        apply(new BillCreatedEvent(
            command.getBillId(),
            command.getTitle(),
            command.getTotal(),
            command.getVendor(),
            command.getDueDate()
        ));
    }

    /**
     * Attaches a file to the bill.
     *
     * @param command the attach file command
     */
    @CommandHandler
    public void handle(AttachFileCommand command) {
        if (status != BillStatus.DRAFT) {
            throw new IllegalStateException("Files can only be attached to draft bills");
        }

        apply(new FileAttachedEvent(
            billId,
            command.getFileId(),
            command.getFileName(),
            command.getFileSize(),
            command.getContentType()
        ));
    }

    /**
     * Approves the bill for payment.
     *
     * @param command the approve bill command
     */
    @CommandHandler
    public void handle(ApproveBillCommand command) {
        if (status != BillStatus.COMPLETED) {
            throw new IllegalStateException("Only completed bills can be approved");
        }

        apply(new BillApprovedEvent(
            billId,
            command.getApprovedBy(),
            command.getComments(),
            Instant.now()
        ));
    }

    @EventSourcingHandler
    public void on(BillCreatedEvent event) {
        this.billId = event.getBillId();
        this.title = event.getTitle();
        this.total = event.getTotal();
        this.status = BillStatus.DRAFT;
    }

    @EventSourcingHandler
    public void on(FileAttachedEvent event) {
        this.status = BillStatus.PENDING;
    }

    @EventSourcingHandler
    public void on(BillApprovedEvent event) {
        this.status = BillStatus.APPROVED;
    }
}
```

### Testing Standards

#### Unit Testing

```java
@ExtendWith(MockitoExtension.class)
class BillAggregateTest {

    private static final String TEST_TITLE = "Test Bill";
    private static final BigDecimal TEST_TOTAL = new BigDecimal("100.00");
    private static final String TEST_VENDOR = "Test Vendor";

    @Test
    @DisplayName("Should create bill with valid command")
    void shouldCreateBillWithValidCommand() {
        // Given
        BillId billId = BillId.generate();
        CreateBillCommand command = new CreateBillCommand(
            billId, TEST_TITLE, TEST_TOTAL, TEST_VENDOR, null
        );

        // When
        BillAggregate aggregate = new BillAggregate(command);

        // Then
        assertThat(aggregate.getTitle()).isEqualTo(TEST_TITLE);
        assertThat(aggregate.getTotal()).isEqualTo(TEST_TOTAL);
        assertThat(aggregate.getStatus()).isEqualTo(BillStatus.DRAFT);
    }

    @Test
    @DisplayName("Should throw exception when creating bill with null title")
    void shouldThrowExceptionWhenCreatingBillWithNullTitle() {
        // Given
        BillId billId = BillId.generate();
        CreateBillCommand command = new CreateBillCommand(
            billId, null, TEST_TOTAL, TEST_VENDOR, null
        );

        // When & Then
        assertThatThrownBy(() -> new BillAggregate(command))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Title is required");
    }
}
```

#### Integration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "axon.axonserver.servers=localhost:8124"
})
@Transactional
class BillCommandControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private StorageService storageService;

    @Test
    @DisplayName("Should create bill and return 201")
    void shouldCreateBillAndReturn201() {
        // Given
        BillCreateRequest request = new BillCreateRequest(
            "Integration Test Bill",
            new BigDecimal("250.00"),
            "Integration Vendor",
            LocalDate.now().plusDays(30)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BillCreateRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<BillResponse> response = restTemplate.postForEntity(
            "/api/commands/bills", entity, BillResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getTitle()).isEqualTo(request.getTitle());
        assertThat(response.getBody().getStatus()).isEqualTo("DRAFT");
    }
}
```

### Documentation Standards

All public APIs must have comprehensive JavaDoc:

```java
/**
 * REST controller for handling bill commands.
 *
 * <p>This controller provides endpoints for creating, updating,
 * and managing bills through CQRS command operations.</p>
 *
 * @author Billing Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/commands/bills")
@Validated
@Slf4j
public class BillCommandController {

    private final CommandGateway commandGateway;

    /**
     * Creates a new bill with the provided information.
     *
     * @param request the bill creation request containing bill details
     * @return the created bill information
     * @throws MethodArgumentNotValidException if request validation fails
     * @throws CommandExecutionException if command execution fails
     * @return ResponseEntity containing created bill with HTTP status 201
     */
    @PostMapping
    public ResponseEntity<BillResponse> createBill(
            @Valid @RequestBody BillCreateRequest request) {
        log.info("Creating new bill: {}", request.getTitle());

        BillId billId = BillId.generate();
        CreateBillCommand command = new CreateBillCommand(
            billId,
            request.getTitle(),
            request.getTotal(),
            request.getVendor(),
            request.getDueDate()
        );

        try {
            CompletableFuture<Object> result = commandGateway.send(command);
            result.get(5, TimeUnit.SECONDS); // Wait for command completion

            BillResponse response = new BillResponse(
                billId.getValue(),
                request.getTitle(),
                request.getTotal(),
                request.getVendor(),
                request.getDueDate(),
                BillStatus.DRAFT,
                Instant.now()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to create bill", e);
            throw new CommandExecutionException("Failed to create bill", e);
        }
    }
}
```

## Database Development

### Local Database Setup

```bash
# Access PostgreSQL container
docker exec -it billing-postgres psql -U billing_user -d billing_db

# Common database operations
\l                          # List databases
\c billing_db              # Connect to billing database
\dt                         # List tables
\d bill_summary            # Describe table
\d+ bill_summary           # Show table with details
```

### Database Migrations

The application uses JPA with Hibernate for schema management:

```yaml
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update     # Auto-update schema in dev
    show-sql: true         # Log SQL statements
    properties:
      hibernate:
        format_sql: true   # Format SQL for readability
        use_sql_comments: true
```

### Query Optimization

Add indexes for common query patterns:

```sql
-- Index for bill status queries
CREATE INDEX idx_bill_summary_status ON bill_summary(status);

-- Index for date-based queries
CREATE INDEX idx_bill_summary_created_at ON bill_summary(created_at);

-- Index for vendor searches
CREATE INDEX idx_bill_summary_vendor ON bill_summary(vendor);

-- Composite index for complex queries
CREATE INDEX idx_bill_summary_status_created ON bill_summary(status, created_at DESC);
```

## API Development

### API Design Principles

1. **RESTful Design**: Use proper HTTP methods and status codes
2. **CQRS Separation**: Separate command and query endpoints
3. **Consistent Response Structure**: Standardized error and success responses
4. **Input Validation**: Comprehensive request validation
5. **Error Handling**: Clear, actionable error messages

### Response Standards

```java
// Success Response
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private Instant timestamp;

    // Constructors, getters, setters
}

// Error Response
public class ErrorResponse {
    private String code;
    private String message;
    private List<String> details;
    private Instant timestamp;

    // Constructors, getters, setters
}
```

### Input Validation

```java
public class BillCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @NotNull(message = "Total is required")
    @DecimalMin(value = "0.01", message = "Total must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Total must have valid format")
    private BigDecimal total;

    @Size(max = 100, message = "Vendor name cannot exceed 100 characters")
    private String vendor;

    @Future(message = "Due date must be in the future")
    private LocalDate dueDate;

    // Validation groups for different contexts
    public interface CreateValidation {}
    public interface UpdateValidation {}
}
```

## Testing Guidelines

### Test Structure

```
src/test/java/
├── unit/                          # Fast unit tests
│   ├── domain/
│   │   ├── BillAggregateTest.java
│   │   └── BillValidationServiceTest.java
│   ├── application/
│   │   ├── BillCommandHandlerTest.java
│   │   └── BillQueryHandlerTest.java
│   └── infrastructure/
│       ├── OcrServiceTest.java
│       └── StorageServiceTest.java
├── integration/                   # Integration tests with real dependencies
│   ├── api/
│   │   ├── BillCommandControllerTest.java
│   │   └── BillQueryControllerTest.java
│   ├── database/
│   │   └── BillProjectionRepositoryTest.java
│   └── external/
│       └── OcrClientTest.java
└── e2e/                          # End-to-end tests
    ├── BillWorkflowE2ETest.java
    └── FileProcessingE2ETest.java
```

### Test Data Management

```java
@TestConfiguration
public class TestDataConfig {

    @Bean
    @Primary
    public DataSource testDataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .addScript("test-data.sql")
            .build();
    }
}

// Test utilities
public class TestDataFactory {

    public static BillCreateRequest createBillRequest() {
        return new BillCreateRequest(
            "Test Bill",
            new BigDecimal("100.00"),
            "Test Vendor",
            LocalDate.now().plusDays(30)
        );
    }

    public static CreateBillCommand createBillCommand() {
        return new CreateBillCommand(
            BillId.generate(),
            "Test Bill",
            new BigDecimal("100.00"),
            "Test Vendor",
            LocalDate.now().plusDays(30)
        );
    }
}
```

### Performance Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.hikari.maximum-pool-size=20",
    "spring.datasource.hikari.minimum-idle=5"
})
class PerformanceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should handle concurrent bill creation")
    void shouldHandleConcurrentBillCreation() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        createBill();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify all bills were created successfully
        // Add assertions for database state
    }
}
```

## Debugging

### Local Debugging Setup

1. **IDE Configuration**: Configure remote debugging in IntelliJ IDEA
2. **JVM Options**: Add debug flags to application startup
3. **Breakpoint Configuration**: Strategic breakpoints for debugging

```bash
# Enable remote debugging
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar target/billing-backend.jar

# Or use Maven debug profile
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### Event Sourcing Debugging

Use Axon Dashboard to debug event flow:

1. **Access Dashboard**: http://localhost:8024
2. **View Events**: Monitor event stream in real-time
3. **Inspect Aggregates**: Check aggregate state
4. **Debug Commands**: Trace command execution
5. **Event Replay**: Rebuild state from events

### Logging Configuration

Configure detailed logging for debugging:

```yaml
# application-dev.yml
logging:
  level:
    com.acme.billing: DEBUG
    org.axonframework: DEBUG
    org.springframework.data.jpa: DEBUG
    com.zaxxer.hikari: DEBUG

  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%logger{36}] - %msg%n"

# Log SQL with parameters
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        type: trace
```

## Common Development Issues

### 1. Axon Server Connection Issues

```bash
# Check if Axon Server is running
curl http://localhost:8024/v1/public/context

# Reset Axon Server data
docker compose down
docker volume rm billing_axon_data billing_axon_events
docker compose up -d axonserver
```

### 2. Database Schema Issues

```bash
# Recreate database
docker exec billing-postgres psql -U billing_user -d postgres -c "DROP DATABASE IF EXISTS billing_db;"
docker exec billing-postgres psql -U billing_user -d postgres -c "CREATE DATABASE billing_db;"
```

### 3. File Upload Issues

```bash
# Check MinIO access
curl http://localhost:9000/minio/health/live

# Reset MinIO data
docker compose down
docker volume rm billing_minio_data
docker compose up -d minio
```

### 4. Port Conflicts

```bash
# Check port usage
netstat -tulpn | grep :8080

# Kill processes using port
sudo fuser -k 8080/tcp
```

## Performance Optimization

### JVM Tuning

```bash
# Development JVM options
export JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:+PrintGCDetails"

# Production JVM options
export JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+UseStringDeduplication"
```

### Database Performance

```sql
-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM bill_summary WHERE status = 'PENDING';

-- Check index usage
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE tablename = 'bill_summary';

-- Monitor slow queries
SELECT query, mean_time, calls
FROM pg_stat_statements
WHERE mean_time > 1000
ORDER BY mean_time DESC;
```

## Code Review Guidelines

### Review Checklist

- [ ] Code follows Java style guide
- [ ] Adequate test coverage (>80%)
- [ ] No hardcoded credentials or sensitive data
- [ ] Proper error handling and logging
- [ ] Input validation on all public APIs
- [ ] Database queries optimized
- [ ] Documentation updated if needed
- [ ] Performance implications considered
- [ ] Security implications reviewed

### Review Process

1. **Self-Review**: Author reviews own changes first
2. **Peer Review**: At least one team member review
3. **Automated Checks**: CI/CD pipeline validation
4. **Integration Testing**: Verify with existing system
5. **Documentation**: Update relevant documentation

This comprehensive development guide should help new team members get up to speed quickly and maintain high code quality standards.
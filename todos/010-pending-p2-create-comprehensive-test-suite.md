---
status: pending
priority: p2
issue_id: "010"
tags: [testing, unit-tests, integration-tests, quality-assurance]
dependencies: ["003", "004", "007", "008"]
---

# Create Comprehensive Unit and Integration Tests

## Problem Statement
Develop comprehensive test suite covering unit tests for domain logic and integration tests for complete workflows. This ensures code quality, validates CQRS event handling, and enables confident refactoring.

## Findings
- No test coverage for business logic
- Cannot validate CQRS event flows
- Regression detection impossible
- Confidence in code changes low
- Production deployment risk high
- Location: `backend/src/test/java/com/acme/billing/` (test package structure to be created)

## Proposed Solutions

### Option 1: Implement comprehensive test suite with multiple testing approaches
- **Pros**: Ensures code quality, validates CQRS patterns, enables confident deployments
- **Cons**: Significant test writing effort, requires test setup and configuration
- **Effort**: Large (2-3 days)
- **Risk**: Low (Testing frameworks well-established)

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/test/java/com/acme/billing/` (entire test package)
  - `backend/src/test/resources/application-test.yml` (test configuration)
  - `backend/pom.xml` (test dependencies)
- **Related Components**: All application components
- **Database Changes**: No (test databases managed by test frameworks)

## Test Categories:

### 1. Unit Tests (Axon TestFixture):
```java
@Test
void whenCreateBillCommand_thenBillCreatedEventPublished() {
    fixture.givenNoPriorActivity()
        .when(CreateBillCommand.builder()
            .title("Test Bill")
            .total(new BigDecimal("100.00"))
            .build())
        .expectEvents(BillCreatedEvent.builder()
            .title("Test Bill")
            .total(new BigDecimal("100.00"))
            .build());
}
```

### 2. Domain Logic Tests:
- **BillAggregateTest**: Command handling and business rules
- **ServiceTests**: Storage, OCR, notification services
- **RepositoryTests**: Data access layer validation

### 3. Integration Tests:
- **API Integration Tests**: End-to-end REST workflows
- **CQRS Integration**: Command to event to projection flows
- **External Service Integration**: MinIO, OCR service, email

### 4. Test Scenarios:
- **Bill Creation Workflow**: Create → File Upload → OCR → Approval
- **Error Handling**: Invalid commands, service failures
- **Data Consistency**: Event to projection synchronization
- **Performance**: Load testing for read/write operations

## Test Dependencies Required:
- **Axon TestFixture**: For CQRS testing
- **JUnit 5**: Test framework
- **Mockito**: Mock external dependencies
- **TestContainers**: PostgreSQL and Redis for integration tests
- **Spring Boot Test**: Application context testing
- **AssertJ**: Fluent assertions

## Test Configuration:
```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
axon:
  axonserver:
    servers: localhost:8024
```

## Resources
- Original finding: GitHub issue triage
- Related issues: #003 (BillAggregate), #004 (DTOs), #007 (Controllers), #008 (Projections)
- Axon Testing Guide: https://docs.axoniq.io/reference-guide/47-testing
- Spring Boot Testing: https://spring.io/guides/gs/testing-web/

## Acceptance Criteria
- [ ] Unit test coverage > 80% for domain logic
- [ ] Axon TestFixture tests for all command handlers
- [ ] Integration tests for complete API workflows
- [ ] Event projection tests for read model consistency
- [ ] Mock external services (OCR, storage, email)
- [ ] Test Containers setup for PostgreSQL integration
- [ ] Test data factories and builders
- [ ] Performance tests for read/write operations
- [ ] CI/CD pipeline integration with test execution
- [ ] Test reports and coverage metrics

## Test Structure:
```
src/test/java/com/acme/billing/
├── domain/
│   ├── BillAggregateTest.java
│   └── EventHandlerTest.java
├── service/
│   ├── StorageServiceTest.java
│   ├── OcrServiceTest.java
│   └── NotificationServiceTest.java
├── web/rest/
│   ├── BillCommandControllerTest.java
│   └── BillQueryControllerTest.java
├── projection/
│   └── ProjectionEventHandlerTest.java
└── integration/
    ├── BillWorkflowIntegrationTest.java
    └── ExternalServiceIntegrationTest.java
```

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P2 (IMPORTANT)
- Estimated effort: Large (2-3 days)

**Learnings:**
- Comprehensive testing is essential for CQRS systems due to their complexity
- Axon TestFixture provides specialized testing for command/event flows
- Integration tests validate complete business workflows
- Test coverage enables confident refactoring and deployments

## Notes
Source: Triage session on 2025-01-22
Dependencies: Should be implemented after core components (#003, #004, #007, #008) are complete
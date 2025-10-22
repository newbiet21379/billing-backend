---
status: pending
priority: p1
issue_id: "008"
tags: [projections, jpa, postgresql, read-model]
dependencies: ["003", "002"]
---

# Implement Query Side Projections with JPA

## Problem Statement
Create the read model projection layer using Spring Data JPA and PostgreSQL. This handles querying bill data efficiently by maintaining materialized views from domain events, enabling fast read operations separate from the write model.

## Findings
- No efficient querying capability for bill data
- Cannot generate reports or list views
- Read operations would be slow without dedicated read model
- CQRS pattern incomplete without projection layer
- Location: `backend/src/main/java/com/acme/billing/projection/` (package to be created)

## Proposed Solutions

### Option 1: Implement comprehensive JPA projection system
- **Pros**: Enables efficient read queries and reporting capabilities
- **Cons**: Complex event handling and database schema design required
- **Effort**: Large (1-2 days)
- **Risk**: Medium (Event-to-projection mapping complexity)

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/main/java/com/acme/billing/projection/BillProjection.java`
  - `backend/src/main/java/com/acme/billing/projection/BillFileProjection.java`
  - `backend/src/main/java/com/acme/billing/projection/ProjectionEventHandler.java`
  - `backend/src/main/java/com/acme/billing/repository/BillReadRepository.java`
  - `backend/src/main/resources/application.yml` (JPA config)
- **Related Components**: PostgreSQL, Axon Event Handlers, Query Controllers
- **Database Changes**: Yes (create tables for read model)

## Projections to Implement:

### BillProjection:
```java
@Entity
@Table(name = "bill_read_model")
public class BillProjection {
    @Id private String id;
    private String title;
    private BigDecimal total;
    private String status; // CREATED, FILE_ATTACHED, PROCESSED, APPROVED, REJECTED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String ocrExtractedText;
    private BigDecimal ocrExtractedTotal;
    private String approverId;
    private String approvalReason;
    // ... other fields
}
```

### BillFileProjection:
```java
@Entity
@Table(name = "bill_file_read_model")
public class BillFileProjection {
    @Id private String id;
    private String billId;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String storagePath;
    private LocalDateTime uploadedAt;
}
```

## Event Handlers to Implement:
- **BillCreatedEvent**: Create new BillProjection
- **FileAttachedEvent**: Create BillFileProjection, update BillProjection status
- **OcrCompletedEvent**: Update BillProjection with OCR results
- **BillApprovedEvent**: Update BillProjection approval information

## Repository Interfaces:
- **BillReadRepository**: Custom queries, filtering, pagination
- **BillFileReadRepository**: File attachment queries

## Database Schema Requirements:
- Primary keys and foreign key relationships
- Indexes on frequently queried fields (status, dates)
- Proper data types for numeric and text fields
- Audit fields (created_at, updated_at)

## Resources
- Original finding: GitHub issue triage
- Related issues: #003 (BillAggregate), #002 (PostgreSQL in Docker), #007 (Query Controllers)
- Spring Data JPA Documentation: https://spring.io/projects/spring-data-jpa
- Axon Projections Guide: https://docs.axoniq.io/reference-guide/47-projections

## Acceptance Criteria
- [ ] BillProjection JPA entity created with proper annotations
- [ ] BillFileProjection JPA entity implemented
- [ ] Event handlers process all relevant domain events
- [ ] Spring Data repositories with custom queries
- [ ] Database schema created with proper indexes
- [ ] Integration with PostgreSQL container
- [ ] Query handlers connect repositories to Axon query gateway
- [ ] Unit tests for projection logic
- [ ] Integration tests for event-to-projection flow
- [ ] Performance testing for query operations

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P1 (CRITICAL)
- Estimated effort: Large (1-2 days)

**Learnings:**
- Projections are essential for CQRS read model performance
- Event handling logic is critical for maintaining data consistency
- Proper database design enables efficient querying and reporting

## Notes
Source: Triage session on 2025-01-22
Dependencies: Requires #003 (BillAggregate events) and #002 (PostgreSQL setup)
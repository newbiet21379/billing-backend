---
status: pending
priority: p1
issue_id: "003"
tags: [domain, cqrs, event-sourcing, axon]
dependencies: []
---

# Implement BillAggregate with Axon Framework

## Problem Statement
The core domain object (BillAggregate) must be implemented using Axon Framework patterns to handle CQRS commands and emit events. This is the heart of the billing system's event sourcing architecture.

## Findings
- No domain model exists to handle bill operations
- Cannot process CreateBillCommand, AttachFileCommand, or ApproveBillCommand
- Event sourcing foundation missing
- All subsequent CQRS implementation depends on this
- Location: `backend/src/main/java/com/acme/billing/domain/BillAggregate.java` (to be created)

## Proposed Solutions

### Option 1: Implement full BillAggregate with Axon patterns
- **Pros**: Establishes core domain logic and event foundation
- **Cons**: Complex implementation requiring Axon Framework expertise
- **Effort**: Large (1-2 days)
- **Risk**: Medium (Axon Framework learning curve)

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/main/java/com/acme/billing/domain/BillAggregate.java`
  - Command DTOs (to be created)
  - Event DTOs (to be created)
- **Related Components**: Axon Framework, Event Store, Command Handlers
- **Database Changes**: No (event sourcing handled by Axon Server)

## Commands to Handle:
- **CreateBillCommand**: Create new bill with title and total
- **AttachFileCommand**: Attach file information to bill
- **ApplyOcrResultCommand**: Apply OCR processing results
- **ApproveBillCommand**: Approve or reject bill

## Events to Emit:
- **BillCreatedEvent**: New bill created
- **FileAttachedEvent**: File attached to bill
- **OcrRequestedEvent**: OCR processing triggered
- **OcrCompletedEvent**: OCR processing completed
- **BillApprovedEvent**: Bill approval decision made

## Resources
- Original finding: GitHub issue triage
- Related issues: #001 (project structure), #002 (Docker setup)
- Axon Framework Documentation: https://docs.axoniq.io/

## Acceptance Criteria
- [ ] BillAggregate implemented with @Aggregate annotation
- [ ] Command handlers created for all bill operations
- [ ] Event sourcing handlers implemented
- [ ] Business rules and validation logic added
- [ ] Bill state management (CREATED → FILE_ATTACHED → PROCESSED → APPROVED/REJECTED)
- [ ] Aggregate properly handles Axon lifecycle
- [ ] Unit tests written for aggregate behavior

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P1 (CRITICAL)
- Estimated effort: Large (1-2 days)

**Learnings:**
- BillAggregate is the foundation for all CQRS functionality
- Proper event sourcing implementation is critical for system integrity
- This task enables all subsequent command and query handling

## Notes
Source: Triage session on 2025-01-22
Dependencies: Requires project structure (#001) and Axon dependencies to be configured
---
status: pending
priority: p2
issue_id: "004"
tags: [cqrs, dto, api, axon]
dependencies: ["003"]
---

# Create Command and Query DTOs

## Problem Statement
Need to create all command and query Data Transfer Objects (DTOs) for the billing system's CQRS pattern. These define the contracts for all operations and must follow Axon Framework conventions.

## Findings
- No command/query contracts defined
- Cannot implement REST endpoints without DTOs
- Axon Framework requires properly annotated command/query objects
- API contracts undefined
- Location: `backend/src/main/java/com/acme/billing/api/` (directories to be created)

## Proposed Solutions

### Option 1: Create comprehensive CQRS DTO structure
- **Pros**: Defines clear API contracts and enables Axon Framework integration
- **Cons**: Requires careful design to ensure proper validation and serialization
- **Effort**: Medium (4-6 hours)
- **Risk**: Low

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/main/java/com/acme/billing/api/commands/` (package)
  - `backend/src/main/java/com/acme/billing/api/queries/` (package)
  - `backend/src/main/java/com/acme/billing/api/dto/` (package)
- **Related Components**: Axon Framework, REST Controllers, Validation
- **Database Changes**: No

## DTOs to Create:

### Commands:
- **CreateBillCommand**: title, total, metadata
- **AttachFileCommand**: billId, filename, contentType, fileSize
- **ApplyOcrResultCommand**: billId, extractedText, extractedTotal, extractedTitle
- **ApproveBillCommand**: billId, approverId, decision, reason

### Queries:
- **FindBillQuery**: billId
- **ListBillsQuery**: filters, pagination

### Response DTOs:
- **BillResponse**: Complete bill information
- **BillSummaryResponse**: List view information
- **OcrResultResponse**: OCR processing results

## Resources
- Original finding: GitHub issue triage
- Related issues: #003 (BillAggregate), #001 (project structure)
- Axon Framework Documentation: https://docs.axoniq.io/

## Acceptance Criteria
- [ ] All command DTOs created with proper @Identifier annotation
- [ ] All query DTOs created with proper constructor
- [ ] Response DTOs created for API responses
- [ ] Validation annotations added to all command fields
- [ ] Jackson serialization/deserialization tested
- [ ] Builder patterns implemented for complex objects
- [ ] Package structure follows Java conventions

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P2 (IMPORTANT)
- Estimated effort: Medium (4-6 hours)

**Learnings:**
- DTOs are the contracts that enable CQRS pattern implementation
- Proper validation and serialization are critical for API reliability
- This task enables both aggregate and controller implementation

## Notes
Source: Triage session on 2025-01-22
Dependencies: Should follow #003 (BillAggregate) design patterns
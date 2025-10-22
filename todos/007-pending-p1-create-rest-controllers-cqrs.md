---
status: pending
priority: p1
issue_id: "007"
tags: [rest-api, spring-boot, cqrs, controllers]
dependencies: ["003", "004"]
---

# Create REST Controllers for Commands and Queries

## Problem Statement
Implement Spring Boot REST controllers to expose the CQRS command and query endpoints. These controllers provide the HTTP interface for all bill operations and must follow RESTful conventions with proper error handling.

## Findings
- No HTTP endpoints for bill operations
- Cannot test or use the billing system via API
- No external integration capability
- CQRS implementation not accessible
- Location: `backend/src/main/java/com/acme/billing/web/rest/` (package to be created)

## Proposed Solutions

### Option 1: Implement comprehensive REST controllers
- **Pros**: Provides complete HTTP interface for all billing operations
- **Cons**: Requires careful error handling and validation implementation
- **Effort**: Medium (4-6 hours)
- **Risk**: Low (Spring Boot REST controller patterns well-established)

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/main/java/com/acme/billing/web/rest/BillCommandController.java`
  - `backend/src/main/java/com/acme/billing/web/rest/BillQueryController.java`
  - `backend/src/main/java/com/acme/billing/web/errors/GlobalExceptionHandler.java`
- **Related Components**: Axon Command/Query Gateways, DTOs, BillAggregate
- **Database Changes**: No

## Controllers to Implement:

### BillCommandController:
- `POST /api/commands/bills` - Create new bill
  - Request: CreateBillCommand
  - Response: BillResponse (with generated ID)
- `POST /api/commands/bills/{id}/file` - Upload file to bill
  - Request: MultipartFile + metadata
  - Response: FileAttachmentResponse
- `POST /api/commands/bills/{id}/approve` - Approve/reject bill
  - Request: ApproveBillCommand
  - Response: BillResponse (updated status)

### BillQueryController:
- `GET /api/queries/bills/{id}` - Get specific bill details
  - Response: BillResponse
- `GET /api/queries/bills` - List bills with filtering
  - Parameters: status, date range, pagination
  - Response: List<BillSummaryResponse>

### Error Handling:
- **GlobalExceptionHandler**: RFC 7807 compliant error responses
- **Validation Errors**: 400 Bad Request with field details
- **Command Failures**: 400/422 with business rule violations
- **Not Found**: 404 for missing resources
- **Server Errors**: 500 with error tracking ID

## Integration Requirements:
- Axon Command Gateway for command dispatch
- Axon Query Gateway for query execution
- Spring Validation for request validation
- SpringDoc OpenAPI for documentation
- Content negotiation (JSON)

## Resources
- Original finding: GitHub issue triage
- Related issues: #003 (BillAggregate), #004 (DTOs), #001 (project structure)
- Spring Boot REST Documentation: https://spring.io/guides/gs/rest-service/
- SpringDoc OpenAPI: https://springdoc.org/

## Acceptance Criteria
- [ ] BillCommandController implemented with all command endpoints
- [ ] BillQueryController implemented with all query endpoints
- [ ] GlobalExceptionHandler for RFC 7807 compliant error responses
- [ ] Request validation with proper error responses
- [ ] OpenAPI documentation available at /swagger-ui.html
- [ ] Command and Query Gateway integration
- [ ] Unit tests for all endpoints
- [ ] Integration tests for complete request/response cycles
- [ ] Proper HTTP status codes and content types

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P1 (CRITICAL)
- Estimated effort: Medium (4-6 hours)

**Learnings:**
- REST controllers expose the CQRS system to external clients
- Proper error handling is critical for API reliability
- OpenAPI documentation enables easy API discovery and testing

## Notes
Source: Triage session on 2025-01-22
Dependencies: Requires #003 (BillAggregate) and #004 (DTOs) to be implemented first
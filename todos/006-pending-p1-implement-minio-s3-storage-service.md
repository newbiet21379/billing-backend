---
status: pending
priority: p1
issue_id: "006"
tags: [storage, minio, s3, spring-boot]
dependencies: ["002"]
---

# Implement MinIO S3 Integration Service

## Problem Statement
Create a Spring Boot service for file storage operations using MinIO S3-compatible storage. This service handles bill file uploads, downloads, and presigned URL generation for secure file access.

## Findings
- No file storage capability for uploaded bills
- Cannot persist bill files beyond OCR processing
- No secure file access mechanism
- Bill files are lost when containers restart
- Location: `backend/src/main/java/com/acme/billing/service/StorageService.java` (to be created)

## Proposed Solutions

### Option 1: Implement comprehensive MinIO storage service
- **Pros**: Provides persistent, scalable file storage with secure access
- **Cons**: Requires S3/MinIO configuration and security considerations
- **Effort**: Medium (3-5 hours)
- **Risk**: Low (Spring Boot S3 integration is well-documented)

## Recommended Action
[Leave blank - will be filled during approval]

## Technical Details
- **Affected Files**:
  - `backend/src/main/java/com/acme/billing/service/StorageService.java`
  - `backend/src/main/java/com/acme/billing/config/MinioConfig.java`
  - `backend/src/main/resources/application.yml` (S3 configuration)
- **Related Components**: MinIO container, File upload controllers, BillAggregate
- **Database Changes**: No (file metadata tracked in event store)

## Service Capabilities:
- **File Upload**: MultipartFile handling with validation
- **File Download**: Stream-based file retrieval
- **Presigned URLs**: Secure time-limited access URLs
- **File Deletion**: Cleanup and management operations
- **Metadata Storage**: File information tracking
- **Error Handling**: Comprehensive exception management

## Configuration Required:
```yaml
spring:
  s3:
    endpoint: http://minio:9000
    access-key: minioadmin
    secret-key: minioadmin
    region: us-east-1
billing:
  storage:
    bucket-name: bills
    max-file-size: 10MB
    allowed-content-types: [application/pdf, image/jpeg, image/png]
```

## Dependencies Required:
- Spring Boot S3 Starter
- AWS Java SDK S3
- Validation annotations

## Resources
- Original finding: GitHub issue triage
- Related issues: #002 (Docker Compose MinIO setup), #001 (project structure)
- Spring Boot S3 Documentation: https://spring.io/guides/gs/uploading-files/
- MinIO Documentation: https://min.io/docs/minio/linux/index.html

## Acceptance Criteria
- [ ] StorageService implemented with MinIO S3 client
- [ ] File upload functionality with validation (size, content type)
- [ ] Presigned URL generation for secure file access
- [ ] File deletion and cleanup capabilities
- [ ] Error handling and retry logic for S3 operations
- [ ] Integration with Spring Boot multipart file handling
- [ ] Configuration properties for MinIO connection
- [ ] Unit tests for storage operations
- [ ] Integration with MinIO container via Docker Compose

## Work Log

### 2025-01-22 - Initial Discovery
**By:** Claude Triage System
**Actions:**
- Issue discovered during GitHub issue triage
- Categorized as P1 (CRITICAL)
- Estimated effort: Medium (3-5 hours)

**Learnings:**
- File storage is essential for bill persistence and audit trails
- MinIO provides S3-compatible storage with local development ease
- Presigned URLs enable secure file access without exposing storage credentials

## Notes
Source: Triage session on 2025-01-22
Dependencies: Requires #002 (Docker Compose) to have MinIO container available
# Storage Service Documentation

## Overview

The Storage Service provides comprehensive file storage operations using MinIO S3-compatible storage. It handles file uploads, downloads, presigned URL generation, and file management for the billing application.

## Features

- **File Upload**: Secure file upload with validation (size, content type)
- **File Download**: Stream-based file retrieval
- **Presigned URLs**: Time-limited secure access URLs
- **File Deletion**: Cleanup and management operations
- **Metadata Storage**: File information tracking
- **Error Handling**: Comprehensive exception management
- **Bucket Management**: Automatic bucket creation when enabled

## Configuration

### application.yml

```yaml
# MinIO S3 Configuration
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  region: ${MINIO_REGION:us-east-1}

# Billing Application Configuration
billing:
  storage:
    bucket-name: ${STORAGE_BUCKET_NAME:billing-documents}
    max-file-size: ${STORAGE_MAX_FILE_SIZE:10MB}
    allowed-content-types:
      - ${STORAGE_CONTENT_TYPE_1:application/pdf}
      - ${STORAGE_CONTENT_TYPE_2:image/jpeg}
      - ${STORAGE_CONTENT_TYPE_3:image/png}
      - ${STORAGE_CONTENT_TYPE_4:image/jpg}
    presigned-url-expiration-minutes: ${STORAGE_PRESIGNED_EXPIRATION:60}
    auto-create-bucket: ${STORAGE_AUTO_CREATE_BUCKET:true}
    multipart-upload-enabled: ${STORAGE_MULTIPART_ENABLED:true}
    multipart-part-size-mb: ${STORAGE_MULTIPART_PART_SIZE:5}
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MINIO_ENDPOINT` | MinIO server endpoint | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO access key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret key | `minioadmin` |
| `MINIO_REGION` | MinIO region | `us-east-1` |
| `STORAGE_BUCKET_NAME` | Default bucket name | `billing-documents` |
| `STORAGE_MAX_FILE_SIZE` | Maximum file size | `10MB` |
| `STORAGE_PRESIGNED_EXPIRATION` | Presigned URL expiration (minutes) | `60` |

## API Endpoints

### Upload File

**POST** `/api/storage/upload`

Uploads a file to MinIO storage.

**Parameters:**
- `file` (MultipartFile): File to upload
- `billId` (String): Bill ID for file organization

**Response:**
```json
{
  "objectKey": "bills/bill-123/2024-01-15-143022-abc123.pdf",
  "originalFilename": "invoice.pdf",
  "contentType": "application/pdf",
  "size": 1024000,
  "message": "File uploaded successfully"
}
```

### Download File

**GET** `/api/storage/download/{objectKey}`

Downloads a file from storage.

**Response:** File stream with appropriate headers

### Generate Presigned URL

**GET** `/api/storage/presigned-url/{objectKey}`

Generates a time-limited secure URL for file access.

**Response:**
```json
{
  "objectKey": "bills/bill-123/2024-01-15-143022-abc123.pdf",
  "presignedUrl": "https://minio-server/billing-documents/bills/bill-123/...?X-Amz-Algorithm=...",
  "message": "Presigned URL generated successfully"
}
```

### Delete File

**DELETE** `/api/storage/{objectKey}`

Deletes a file from storage.

**Response:**
```json
{
  "objectKey": "bills/bill-123/2024-01-15-143022-abc123.pdf",
  "message": "File deleted successfully"
}
```

### Check File Exists

**GET** `/api/storage/exists/{objectKey}`

Checks if a file exists in storage.

**Response:**
```json
{
  "objectKey": "bills/bill-123/2024-01-15-143022-abc123.pdf",
  "exists": true,
  "message": "File exists"
}
```

### Ensure Bucket Exists

**POST** `/api/storage/bucket/ensure`

Ensures the storage bucket exists, creates it if configured to do so.

**Response:**
```json
{
  "message": "Bucket ensured successfully"
}
```

## File Organization

Files are organized in the following structure within the bucket:

```
billing-documents/
├── bills/
│   ├── {billId}/
│   │   ├── {timestamp}-{uuid}.{extension}
│   │   ├── {timestamp}-{uuid}.{extension}
│   │   └── ...
│   └── ...
└── ...
```

Example object key: `bills/bill-123/2024-01-15-143022-abc123.pdf`

## File Validation

### Supported Content Types

- `application/pdf`
- `image/jpeg`
- `image/png`

### File Size Limits

- Default maximum: 10MB
- Configurable via `billing.storage.max-file-size`

### Validation Rules

1. File cannot be empty
2. File must have a name
3. Content type must be allowed
4. File size must not exceed maximum limit

## Error Handling

The service provides specific exception types for different error scenarios:

### FileValidationException

Thrown when file validation fails (size, content type, etc.).

```json
{
  "error": "File validation failed: Content type 'text/plain' is not allowed"
}
```

### FileStorageException

Thrown when storage operations fail (upload, delete, bucket operations).

```json
{
  "error": "File storage failed: MinIO error during file upload"
}
```

### FileRetrievalException

Thrown when file retrieval operations fail (download, presigned URL generation).

```json
{
  "error": "File not found: bills/bill-123/nonexistent.pdf"
}
```

## Security Considerations

1. **Presigned URLs**: Time-limited URLs prevent unauthorized access
2. **Content Type Validation**: Only allowed file types can be uploaded
3. **File Size Limits**: Prevents oversized file uploads
4. **Metadata Storage**: Upload metadata is tracked for auditing
5. **Access Control**: MinIO credentials should be secured via environment variables

## Testing

### Unit Tests

Run unit tests with:
```bash
mvn test -Dtest=StorageServiceTest
```

### Integration Tests

Run integration tests with MinIO container:
```bash
# Set environment variable to enable integration tests
export RUN_INTEGRATION_TESTS=true

# Run integration tests
mvn test -Dtest=StorageServiceIntegrationTest
```

## Usage Examples

### Upload a File

```bash
curl -X POST \
  http://localhost:8080/api/storage/upload \
  -F "file=@/path/to/invoice.pdf" \
  -F "billId=bill-123"
```

### Download a File

```bash
curl -X GET \
  "http://localhost:8080/api/storage/download/bills/bill-123/2024-01-15-143022-abc123.pdf" \
  -o downloaded_file.pdf
```

### Generate Presigned URL

```bash
curl -X GET \
  "http://localhost:8080/api/storage/presigned-url/bills/bill-123/2024-01-15-143022-abc123.pdf"
```

### Delete a File

```bash
curl -X DELETE \
  "http://localhost:8080/api/storage/bills/bill-123/2024-01-15-143022-abc123.pdf"
```

## Monitoring and Logging

The service includes comprehensive logging for:

- File upload/download operations
- Validation failures
- Storage errors
- Bucket operations

Log levels can be configured via `logging.level.com.acme.billing.service` in application.yml.

## Performance Considerations

1. **Streaming**: Files are streamed to/from MinIO to minimize memory usage
2. **Presigned URLs**: Use presigned URLs for direct access when possible
3. **Multipart Uploads**: Enabled for large files (configurable part size)
4. **Connection Pooling**: MinIO client handles connection management

## Troubleshooting

### Common Issues

1. **Connection Refused**: Ensure MinIO server is running and accessible
2. **Invalid Credentials**: Verify MinIO access key and secret key
3. **Bucket Not Found**: Enable `auto-create-bucket` or create manually
4. **File Size Limit**: Check `max-file-size` configuration
5. **Content Type Rejection**: Verify file type is in allowed list

### Debug Commands

Check MinIO connection:
```bash
curl -X GET http://localhost:9000/minio/health/live
```

List bucket contents (using MinIO CLI):
```bash
mc ls local/billing-documents
```

## Dependencies

- `io.minio:minio` - MinIO Java SDK
- `spring-boot-starter-validation` - File validation
- `spring-boot-starter-web` - REST endpoints
- `lombok` - Code generation
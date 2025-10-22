# API Examples and Tools

This directory contains comprehensive API examples, tools, and documentation for the Billing & Expense Processing Service.

## Contents

### 📁 Postman Collection
- **`postman-collection.json`** - Complete Postman collection with all API endpoints
  - Command operations (create, update, approve, reject bills)
  - Query operations (list, search, get details)
  - Storage operations (upload, download)
  - Health and monitoring endpoints
  - Pre-configured variables and tests

### 📁 Shell Scripts
- **`curl-examples.sh`** - Comprehensive bash script with API examples
  - All API endpoints implemented as functions
  - Batch operations and performance testing
  - Complete bill workflow examples
  - Error handling and response validation

### 📁 Sample Requests
- **`sample-requests/`** - Sample request payloads
  - `create-bill.json` - Bill creation request
  - `update-bill.json` - Bill update request
  - `approve-bill.json` - Bill approval request
  - `search-bills.json` - Advanced search request

### 📁 Sample Responses
- **`sample-responses/`** - Sample API responses
  - `create-bill-response.json` - Bill creation response
  - `bill-details-response.json` - Complete bill details with OCR data
  - `list-bills-response.json` - Paginated bill list response
  - `error-response.json` - Error response format

## Quick Start

### Using Postman

1. Import the collection:
   ```bash
   # Open Postman and import the collection file
   open postman-collection.json
   ```

2. Configure environment:
   - Set `baseUrl` to your API endpoint (default: `http://localhost:8080`)
   - Set `apiKey` to your authentication key

3. Test the workflow:
   - Execute "Create Bill" to create a sample bill
   - The collection variables will store the bill ID automatically
   - Use subsequent requests to update, attach files, and approve the bill

### Using Shell Script

1. Make the script executable:
   ```bash
   chmod +x curl-examples.sh
   ```

2. Configure environment variables:
   ```bash
   export BASE_URL="http://localhost:8080"
   export API_KEY="your-api-key"
   ```

3. Run examples:
   ```bash
   # Create a bill
   ./curl-examples.sh create-bill

   # Complete workflow with file attachment
   ./curl-examples.sh workflow /path/to/invoice.pdf

   # Performance test
   ./curl-examples.sh performance 100 10

   # Show all available commands
   ./curl-examples.sh help
   ```

## API Examples

### Basic Operations

```bash
# Create a bill
curl -X POST http://localhost:8080/api/commands/bills \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d @sample-requests/create-bill.json

# List bills
curl -X GET "http://localhost:8080/api/queries/bills?page=0&size=20" \
  -H "X-API-Key: your-api-key"

# Get bill details
curl -X GET http://localhost:8080/api/queries/bills/{billId} \
  -H "X-API-Key: your-api-key"
```

### Advanced Operations

```bash
# Search bills with complex filters
curl -X POST http://localhost:8080/api/queries/bills/search \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d @sample-requests/search-bills.json

# Approve a bill
curl -X POST http://localhost:8080/api/commands/bills/{billId}/approve \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d @sample-requests/approve-bill.json
```

### File Operations

```bash
# Generate upload URL
curl -X POST http://localhost:8080/api/storage/upload-url \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "fileName": "invoice.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576
  }'

# Attach file to bill
curl -X POST http://localhost:8080/api/commands/bills/{billId}/file \
  -H "X-API-Key: your-api-key" \
  -F "file=@/path/to/invoice.pdf"
```

## Testing Workflow

### Complete Bill Processing

1. **Create Bill**: Initialize a new bill in DRAFT status
2. **Attach File**: Upload invoice PDF and trigger OCR processing
3. **Wait for OCR**: System processes file and extracts text data
4. **Review Details**: Verify OCR results and bill information
5. **Approve/Reject**: Make approval decision with comments

### Error Scenarios

- **Validation Errors**: Test with invalid data
- **Business Rule Violations**: Try to approve non-completed bills
- **File Upload Errors**: Test with unsupported file types
- **Authentication Errors**: Test without API key
- **Rate Limiting**: Test with high request volumes

## Performance Testing

### Shell Script Performance Test

```bash
# Run 1000 requests with 50 concurrent connections
./curl-examples.sh performance 1000 50

# Monitor response times and throughput
# Results saved to performance-test-*.txt files
```

### Postman Performance

1. Use Postman's "Collection Runner"
2. Set number of iterations
3. Configure delay between requests
4. Monitor response times and error rates

## Response Formats

### Success Response
```json
{
  "success": true,
  "data": { /* Response data */ },
  "message": "Operation completed successfully",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Error description",
    "details": [ /* Detailed error information */ ]
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/endpoint"
}
```

### Paginated Response
```json
{
  "success": true,
  "data": {
    "content": [ /* Array of items */ ],
    "pageable": {
      "page": 0,
      "size": 20,
      "sort": "createdAt,desc"
    },
    "totalElements": 100,
    "totalPages": 5,
    "first": true,
    "last": false,
    "numberOfElements": 20
  },
  "message": "Data retrieved successfully",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Common Issues and Solutions

### Authentication Problems

**Issue**: 401 Unauthorized
```bash
# Check API key is valid
curl -H "X-API-Key: your-api-key" http://localhost:8080/actuator/health
```

**Issue**: 403 Forbidden
```bash
# Check user permissions
# Review role-based access control configuration
```

### Connection Issues

**Issue**: Connection refused
```bash
# Check if service is running
curl http://localhost:8080/actuator/health

# Check Docker containers
docker compose ps
```

**Issue**: SSL Certificate Error
```bash
# For development, disable SSL verification
curl -k https://localhost:8443/api/queries/bills
```

### File Upload Issues

**Issue**: File too large
```bash
# Check file size limits
grep BILLING_UPLOAD_MAX_SIZE docker-compose.yml

# Compress large files before upload
```

**Issue**: Unsupported file type
```bash
# Supported formats: PDF, JPG, PNG, TIFF, BMP
file --mime-type your-file.pdf
```

## Monitoring and Debugging

### Health Checks

```bash
# Basic health check
curl http://localhost:8080/actuator/health

# Detailed health information
curl http://localhost:8080/actuator/health/details

# Application metrics
curl http://localhost:8080/actuator/metrics
```

### Debug Headers

Add debug headers to see request processing:

```bash
curl -H "X-Debug: true" \
     -H "X-API-Key: your-api-key" \
     http://localhost:8080/api/queries/bills
```

### Logging

Check application logs for troubleshooting:

```bash
# Docker Compose logs
docker compose logs -f backend

# Specific service logs
docker compose logs -f ocr
docker compose logs -f axonserver
```

## Environment Configuration

### Development
```bash
export BASE_URL="http://localhost:8080"
export API_KEY="dev-api-key"
export OUTPUT_DIR="./api/sample-responses"
```

### Staging
```bash
export BASE_URL="https://staging-api.company.com"
export API_KEY="staging-api-key"
export OUTPUT_DIR="./staging-responses"
```

### Production
```bash
export BASE_URL="https://api.company.com"
export API_KEY="prod-api-key"
export OUTPUT_DIR="./production-responses"
```

## Contributing

When contributing new API examples:

1. **Follow naming conventions**: Use kebab-case for files
2. **Include documentation**: Add comments explaining the example
3. **Handle errors**: Include error handling and validation
4. **Test thoroughly**: Verify examples work with current API version
5. **Update documentation**: Keep this README file current

## Support

For API issues and questions:

1. **Check API Documentation**: http://localhost:8080/swagger-ui.html
2. **Review Application Logs**: `docker compose logs -f backend`
3. **Check Health Status**: `curl http://localhost:8080/actuator/health`
4. **Test with Examples**: Use provided shell script examples

---

This directory provides everything needed to test, integrate, and develop with the Billing & Expense Processing Service API.
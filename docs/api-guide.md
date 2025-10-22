# API Guide

This comprehensive guide covers all aspects of the Billing & Expense Processing Service REST API, including endpoints, authentication, error handling, and best practices.

## API Overview

The API is designed following **CQRS (Command Query Responsibility Segregation)** principles with separate endpoints for commands (write operations) and queries (read operations).

### Base URL

- **Development**: `http://localhost:8080`
- **Production**: `https://api.billing.company.com`

### API Documentation

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/api-docs`
- **Postman Collection**: Available in `/api` directory

## Authentication

### API Key Authentication (Development)

```bash
curl -H "X-API-Key: your-api-key" \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/queries/bills
```

### JWT Authentication (Production)

```bash
# First obtain JWT token
curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"user@company.com","password":"password"}'

# Use token in subsequent requests
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/queries/bills
```

## Rate Limiting

- **Default**: 100 requests per minute per IP
- **Authenticated**: 500 requests per minute per user
- **Burst**: 200 requests per minute for authenticated users

Rate limit headers are included in responses:
```bash
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640995200
```

## Command API (Write Operations)

### Bills Management

#### Create Bill

Creates a new bill with the provided information.

```http
POST /api/commands/bills
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Office Supplies Invoice",
  "total": 150.75,
  "vendor": "Staples",
  "dueDate": "2024-12-15",
  "category": "OFFICE_SUPPLIES",
  "description": "Monthly office supplies restocking"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "title": "Office Supplies Invoice",
    "total": 150.75,
    "vendor": "Staples",
    "status": "DRAFT",
    "createdAt": "2024-01-15T10:30:00Z",
    "dueDate": "2024-12-15T00:00:00Z"
  },
  "message": "Bill created successfully",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Status Codes:**
- `201 Created` - Bill created successfully
- `400 Bad Request` - Invalid request data
- `401 Unauthorized` - Authentication required
- `429 Too Many Requests` - Rate limit exceeded

#### Update Bill

Updates an existing bill's information.

```http
PUT /api/commands/bills/{billId}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Updated Office Supplies Invoice",
  "total": 175.50,
  "vendor": "Office Depot",
  "dueDate": "2024-12-20",
  "category": "OFFICE_SUPPLIES",
  "description": "Updated monthly office supplies restocking"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "title": "Updated Office Supplies Invoice",
    "total": 175.50,
    "vendor": "Office Depot",
    "status": "DRAFT",
    "updatedAt": "2024-01-15T11:00:00Z"
  },
  "message": "Bill updated successfully",
  "timestamp": "2024-01-15T11:00:00Z"
}
```

#### Attach File to Bill

Uploads and attaches a file to an existing bill, triggering OCR processing.

```http
POST /api/commands/bills/{billId}/file
Content-Type: multipart/form-data
```

**Request Body:**
```
file: [binary file data]
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "fileId": "file_456e7890-e89b-12d3-a456-426614174000",
    "fileName": "invoice.pdf",
    "fileSize": 1048576,
    "contentType": "application/pdf",
    "uploadedAt": "2024-01-15T10:45:00Z"
  },
  "message": "File uploaded and OCR processing started",
  "timestamp": "2024-01-15T10:45:00Z"
}
```

#### Approve Bill

Approves a bill for payment processing.

```http
POST /api/commands/bills/{billId}/approve
Content-Type: application/json
```

**Request Body:**
```json
{
  "approvedBy": "john.doe@company.com",
  "comments": "Invoice verified and approved for payment",
  "approvedAt": "2024-01-15T14:30:00Z"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "status": "APPROVED",
    "approvedBy": "john.doe@company.com",
    "approvedAt": "2024-01-15T14:30:00Z"
  },
  "message": "Bill approved successfully",
  "timestamp": "2024-01-15T14:30:00Z"
}
```

#### Reject Bill

Rejects a bill with specified reasons.

```http
POST /api/commands/bills/{billId}/reject
Content-Type: application/json
```

**Request Body:**
```json
{
  "rejectedBy": "jane.smith@company.com",
  "reason": "Invalid vendor information",
  "comments": "Vendor address does not match records",
  "rejectedAt": "2024-01-15T14:45:00Z"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "status": "REJECTED",
    "rejectedBy": "jane.smith@company.com",
    "reason": "Invalid vendor information",
    "rejectedAt": "2024-01-15T14:45:00Z"
  },
  "message": "Bill rejected successfully",
  "timestamp": "2024-01-15T14:45:00Z"
}
```

#### Delete Bill

Soft-deletes a bill (archived but not permanently removed).

```http
DELETE /api/commands/bills/{billId}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "status": "DELETED",
    "deletedAt": "2024-01-15T15:00:00Z"
  },
  "message": "Bill deleted successfully",
  "timestamp": "2024-01-15T15:00:00Z"
}
```

## Query API (Read Operations)

### Bills Query

#### List Bills

Retrieves a paginated list of bills with optional filtering.

```http
GET /api/queries/bills?page=0&size=20&sort=createdAt,desc&status=PENDING&vendor=Staples
```

**Query Parameters:**
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20, max: 100)
- `sort` (optional): Sort field and direction (default: createdAt,desc)
- `status` (optional): Filter by bill status (DRAFT, PENDING, PROCESSING, COMPLETED, APPROVED, REJECTED, DELETED)
- `vendor` (optional): Filter by vendor name (partial match)
- `category` (optional): Filter by category
- `dateFrom` (optional): Filter by creation date from (ISO 8601)
- `dateTo` (optional): Filter by creation date to (ISO 8601)
- `minTotal` (optional): Minimum total amount
- `maxTotal` (optional): Maximum total amount

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
        "title": "Office Supplies Invoice",
        "total": 150.75,
        "vendor": "Staples",
        "status": "PENDING",
        "category": "OFFICE_SUPPLIES",
        "createdAt": "2024-01-15T10:30:00Z",
        "dueDate": "2024-12-15T00:00:00Z",
        "updatedAt": "2024-01-15T10:45:00Z"
      }
    ],
    "pageable": {
      "page": 0,
      "size": 20,
      "sort": "createdAt,desc"
    },
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true,
    "numberOfElements": 1
  },
  "message": "Bills retrieved successfully",
  "timestamp": "2024-01-15T16:00:00Z"
}
```

#### Get Bill Details

Retrieves detailed information for a specific bill including OCR data and file attachments.

```http
GET /api/queries/bills/{billId}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "billId": "bill_123e4567-e89b-12d3-a456-426614174000",
    "title": "Office Supplies Invoice",
    "total": 150.75,
    "vendor": "Staples",
    "status": "COMPLETED",
    "category": "OFFICE_SUPPLIES",
    "description": "Monthly office supplies restocking",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T11:30:00Z",
    "dueDate": "2024-12-15T00:00:00Z",
    "ocrData": {
      "extractedText": "STAPLES INVOICE\nInvoice #: INV-2024-001\nDate: 2024-01-10\n\nOffice Supplies:\n- Pens (50) @ $1.50 = $75.00\n- Paper (10 reams) @ $7.00 = $70.00\n\nSubtotal: $145.00\nTax (5%): $5.75\nTotal: $150.75",
      "structuredData": {
        "vendor": "Staples",
        "invoiceNumber": "INV-2024-001",
        "invoiceDate": "2024-01-10",
        "subtotal": 145.00,
        "tax": 5.75,
        "total": 150.75,
        "lineItems": [
          {
            "description": "Pens (50)",
            "unitPrice": 1.50,
            "quantity": 50,
            "total": 75.00
          },
          {
            "description": "Paper (10 reams)",
            "unitPrice": 7.00,
            "quantity": 10,
            "total": 70.00
          }
        ]
      },
      "confidence": 0.95,
      "processedAt": "2024-01-15T11:00:00Z"
    },
    "files": [
      {
        "fileId": "file_456e7890-e89b-12d3-a456-426614174000",
        "fileName": "invoice.pdf",
        "fileSize": 1048576,
        "contentType": "application/pdf",
        "uploadedAt": "2024-01-15T10:45:00Z"
      }
    ],
    "approvalHistory": [
      {
        "action": "APPROVED",
        "performedBy": "john.doe@company.com",
        "performedAt": "2024-01-15T14:30:00Z",
        "comments": "Invoice verified and approved for payment"
      }
    ]
  },
  "message": "Bill details retrieved successfully",
  "timestamp": "2024-01-15T16:15:00Z"
}
```

#### Search Bills

Advanced search with complex filtering criteria.

```http
POST /api/queries/bills/search
Content-Type: application/json
```

**Request Body:**
```json
{
  "filters": {
    "title": {
      "contains": "office",
      "caseSensitive": false
    },
    "total": {
      "min": 100.00,
      "max": 200.00
    },
    "vendor": {
      "in": ["Staples", "Office Depot", "Amazon"]
    },
    "status": ["PENDING", "COMPLETED"],
    "createdDate": {
      "from": "2024-01-01T00:00:00Z",
      "to": "2024-01-31T23:59:59Z"
    }
  },
  "pagination": {
    "page": 0,
    "size": 20,
    "sort": [
      {
        "field": "createdAt",
        "direction": "DESC"
      },
      {
        "field": "total",
        "direction": "ASC"
      }
    ]
  }
}
```

#### Bill Statistics

Retrieves statistical information about bills.

```http
GET /api/queries/bills/statistics?period=MONTHLY&year=2024&month=1
```

**Query Parameters:**
- `period`: DAILY, WEEKLY, MONTHLY, YEARLY
- `year`: Year for statistics
- `month`: Month (for MONTHLY period)
- `week`: Week number (for WEEKLY period)

**Response:**
```json
{
  "success": true,
  "data": {
    "period": "MONTHLY",
    "year": 2024,
    "month": 1,
    "totalBills": 45,
    "totalAmount": 15675.50,
    "averageAmount": 348.34,
    "statusBreakdown": {
      "DRAFT": 5,
      "PENDING": 10,
      "COMPLETED": 20,
      "APPROVED": 8,
      "REJECTED": 2
    },
    "categoryBreakdown": {
      "OFFICE_SUPPLIES": 15,
      "SOFTWARE": 8,
      "UTILITIES": 12,
      "MARKETING": 5,
      "OTHER": 5
    },
    "vendorBreakdown": {
      "Staples": 12,
      "Amazon": 8,
      "Microsoft": 6,
      "Google": 5,
      "Others": 14
    }
  },
  "message": "Statistics retrieved successfully",
  "timestamp": "2024-01-15T16:30:00Z"
}
```

## Storage API

### Generate Upload URL

Generates a pre-signed URL for direct file upload to MinIO.

```http
POST /api/storage/upload-url
Content-Type: application/json
```

**Request Body:**
```json
{
  "fileName": "invoice.pdf",
  "contentType": "application/pdf",
  "fileSize": 1048576,
  "bucket": "bills",
  "expirationMinutes": 15
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "uploadUrl": "https://minio.company.com/bills/invoice.pdf?X-Amz-Algorithm=...&X-Amz-Credential=...&X-Amz-Date=...&X-Amz-Expires=900&X-Amz-SignedHeaders=...&X-Amz-Signature=...",
    "fileId": "file_456e7890-e89b-12d3-a456-426614174000",
    "expirationTime": "2024-01-15T16:45:00Z"
  },
  "message": "Upload URL generated successfully",
  "timestamp": "2024-01-15T16:30:00Z"
}
```

### Download File

Generates a download URL for a stored file.

```http
GET /api/storage/files/{fileId}/download-url
```

**Response:**
```json
{
  "success": true,
  "data": {
    "downloadUrl": "https://minio.company.com/bills/invoice.pdf?X-Amz-Algorithm=...&X-Amz-Credential=...&X-Amz-Date=...&X-Amz-Expires=900&X-Amz-SignedHeaders=...&X-Amz-Signature=...",
    "fileName": "invoice.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576,
    "expirationTime": "2024-01-15T16:45:00Z"
  },
  "message": "Download URL generated successfully",
  "timestamp": "2024-01-15T16:30:00Z"
}
```

## Error Handling

### Standard Error Response Format

All error responses follow a consistent format:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "total",
        "message": "Total amount must be greater than 0"
      },
      {
        "field": "title",
        "message": "Title is required and cannot be blank"
      }
    ]
  },
  "timestamp": "2024-01-15T16:45:00Z",
  "path": "/api/commands/bills"
}
```

### Common Error Codes

| Status Code | Error Code | Description |
|-------------|------------|-------------|
| 400 | VALIDATION_ERROR | Request validation failed |
| 401 | UNAUTHORIZED | Authentication required |
| 403 | FORBIDDEN | Insufficient permissions |
| 404 | NOT_FOUND | Resource not found |
| 409 | CONFLICT | Resource already exists |
| 422 | UNPROCESSABLE_ENTITY | Business rule violation |
| 429 | RATE_LIMIT_EXCEEDED | Too many requests |
| 500 | INTERNAL_SERVER_ERROR | Unexpected server error |
| 503 | SERVICE_UNAVAILABLE | Service temporarily unavailable |

### Specific Error Examples

#### Validation Error (400)

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "total",
        "message": "Total amount must be greater than 0"
      },
      {
        "field": "dueDate",
        "message": "Due date must be in the future"
      }
    ]
  },
  "timestamp": "2024-01-15T16:45:00Z",
  "path": "/api/commands/bills"
}
```

#### Business Rule Violation (422)

```json
{
  "success": false,
  "error": {
    "code": "BUSINESS_RULE_VIOLATION",
    "message": "Only completed bills can be approved",
    "details": [
      {
        "field": "billStatus",
        "message": "Bill status must be COMPLETED before approval"
      }
    ]
  },
  "timestamp": "2024-01-15T16:45:00Z",
  "path": "/api/commands/bills/bill_123/approve"
}
```

#### Resource Not Found (404)

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Bill not found",
    "details": [
      {
        "field": "billId",
        "message": "Bill with ID 'bill_999' does not exist"
      }
    ]
  },
  "timestamp": "2024-01-15T16:45:00Z",
  "path": "/api/queries/bills/bill_999"
}
```

## Best Practices

### Request/Response Guidelines

1. **Use appropriate HTTP methods**: GET for queries, POST/PUT for commands, DELETE for deletions
2. **Include Content-Type headers**: Always specify the content type
3. **Handle pagination**: Use pagination for large result sets
4. **Validate input**: Server validates all input, return clear error messages
5. **Use appropriate status codes**: Follow HTTP standards for response codes

### Error Handling

1. **Check response codes**: Always handle different HTTP status codes appropriately
2. **Parse error details**: Use the error details array for specific validation failures
3. **Implement retry logic**: For 5xx errors and rate limiting
4. **Log errors**: Log error responses for debugging

### Performance Optimization

1. **Use pagination**: For large data sets, use pagination
2. **Filter results**: Use query parameters to filter results
3. **Cache responses**: Cache frequently accessed data
4. **Batch operations**: Use batch endpoints when available
5. **Compress responses**: Use gzip compression for large payloads

### Security

1. **Use HTTPS**: Always use HTTPS in production
2. **Validate input**: Never trust user input
3. **Use authentication**: Include valid authentication headers
4. **Rate limiting**: Respect rate limits
5. **Sanitize output**: Never return sensitive data

## SDK Examples

### Java (Spring Boot)

```java
@RestController
public class BillingClient {

    @Value("${billing.api.url}")
    private String billingApiUrl;

    @Value("${billing.api.key}")
    private String apiKey;

    private RestTemplate restTemplate;

    public BillResponse createBill(BillCreateRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BillCreateRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApiResponse<BillResponse>> response = restTemplate.postForEntity(
            billingApiUrl + "/api/commands/bills",
            entity,
            new ParameterizedTypeReference<ApiResponse<BillResponse>>() {}
        );

        if (response.getStatusCode() == HttpStatus.CREATED) {
            return response.getBody().getData();
        } else {
            throw new BillingApiException("Failed to create bill", response.getBody().getError());
        }
    }
}
```

### JavaScript/Node.js

```javascript
class BillingClient {
    constructor(baseUrl, apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.axios = require('axios').create({
            baseURL: baseUrl,
            headers: {
                'X-API-Key': apiKey,
                'Content-Type': 'application/json'
            }
        });
    }

    async createBill(billData) {
        try {
            const response = await this.axios.post('/api/commands/bills', billData);
            return response.data.data;
        } catch (error) {
            if (error.response) {
                throw new BillingApiError(error.response.data.error);
            }
            throw error;
        }
    }

    async listBills(options = {}) {
        const params = new URLSearchParams(options);
        const response = await this.axios.get(`/api/queries/bills?${params}`);
        return response.data.data;
    }

    async getBillDetails(billId) {
        const response = await this.axios.get(`/api/queries/bills/${billId}`);
        return response.data.data;
    }
}
```

### Python

```python
import requests
from typing import Optional, Dict, Any

class BillingClient:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url
        self.api_key = api_key
        self.session = requests.Session()
        self.session.headers.update({
            'X-API-Key': api_key,
            'Content-Type': 'application/json'
        })

    def create_bill(self, bill_data: Dict[str, Any]) -> Dict[str, Any]:
        response = self.session.post(
            f"{self.base_url}/api/commands/bills",
            json=bill_data
        )
        response.raise_for_status()
        return response.json()['data']

    def list_bills(self, **params) -> Dict[str, Any]:
        response = self.session.get(
            f"{self.base_url}/api/queries/bills",
            params=params
        )
        response.raise_for_status()
        return response.json()['data']

    def get_bill_details(self, bill_id: str) -> Dict[str, Any]:
        response = self.session.get(
            f"{self.base_url}/api/queries/bills/{bill_id}"
        )
        response.raise_for_status()
        return response.json()['data']
```

## Testing

### Unit Testing with Mock

```java
@ExtendWith(MockitoExtension.class)
class BillingClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private BillingClient billingClient;

    @Test
    void shouldCreateBillSuccessfully() {
        // Given
        BillCreateRequest request = new BillCreateRequest(
            "Test Bill", new BigDecimal("100.00"), "Test Vendor"
        );
        BillResponse expectedResponse = new BillResponse();
        expectedResponse.setBillId("bill_123");

        ResponseEntity<ApiResponse<BillResponse>> mockResponse =
            ResponseEntity.ok(new ApiResponse<>(true, expectedResponse, "Success"));

        when(restTemplate.postForEntity(
            anyString(), any(), any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        // When
        BillResponse actualResponse = billingClient.createBill(request);

        // Then
        assertThat(actualResponse.getBillId()).isEqualTo("bill_123");
    }
}
```

### Integration Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "billing.api.url=http://localhost:${local.server.port}",
    "billing.api.key=test-api-key"
})
class BillingApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateBillThroughApi() {
        // Given
        BillCreateRequest request = new BillCreateRequest(
            "Integration Test Bill",
            new BigDecimal("150.00"),
            "Test Vendor"
        );

        // When
        ResponseEntity<ApiResponse<BillResponse>> response = restTemplate.postForEntity(
            "/api/commands/bills",
            request,
            new ParameterizedTypeReference<ApiResponse<BillResponse>>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getTitle()).isEqualTo("Integration Test Bill");
    }
}
```

This comprehensive API guide provides everything needed to integrate with the Billing & Expense Processing Service effectively.
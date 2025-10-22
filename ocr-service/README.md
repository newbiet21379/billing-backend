# OCR Service

FastAPI-based OCR microservice for extracting text and structured data from bill images and PDFs using Tesseract OCR engine.

## 🚀 Features

- **Multi-format Support**: JPEG, PNG, BMP, TIFF, PDF files
- **Intelligent PDF Processing**: Text extraction with OCR fallback for scanned PDFs
- **Structured Data Extraction**: Automatic extraction of totals, dates, invoice numbers, vendor names
- **Production Ready**: Comprehensive error handling, validation, and health checks
- **Containerized**: Docker optimized with multi-stage builds and security best practices
- **Fast & Async**: Built on FastAPI with async file processing
- **Well Documented**: Auto-generated OpenAPI/Swagger documentation
- **Fully Tested**: Comprehensive unit test suite

## 📋 API Endpoints

### Service Status
- `GET /` - Basic service information
- `GET /health` - Detailed health check including Tesseract availability

### OCR Processing
- `POST /ocr` - Main endpoint for extracting text and structured data from uploaded files

### Documentation
- `GET /docs` - Swagger UI (interactive API documentation)
- `GET /redoc` - ReDoc (alternative API documentation)

## 🔧 Installation & Setup

### Prerequisites
- Python 3.11+
- Tesseract OCR engine

### Local Development
```bash
# Clone the repository
git clone <repository-url>
cd ocr-service

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Copy environment configuration
cp .env.example .env

# Run the service
uvicorn app:app --reload --host 0.0.0.0 --port 7070
```

### Docker Deployment
```bash
# Build the image
docker build -t ocr-service .

# Run the container
docker run -p 7070:7070 \
  -e MAX_FILE_SIZE=10485760 \
  -e TESSERACT_CMD=/usr/bin/tesseract \
  ocr-service
```

### Docker Compose (Part of Billing System)
```bash
# From project root
docker compose up ocr
```

## 📊 API Usage

### Basic OCR Request
```bash
curl -X POST "http://localhost:7070/ocr" \
  -H "accept: application/json" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@bill.jpg"
```

### Python Client Example
```python
import requests

# Upload file for OCR processing
with open("bill.jpg", "rb") as f:
    response = requests.post(
        "http://localhost:7070/ocr",
        files={"file": f}
    )
    result = response.json()

    print("Success:", result["success"])
    print("Extracted Text:", result["extracted_text"])
    print("Parsed Fields:", result["parsed_fields"])
    print("Processing Time (ms):", result["metadata"]["processing_time_ms"])
```

### Response Structure
```json
{
  "success": true,
  "extracted_text": "Full extracted text from the document...",
  "parsed_fields": {
    "total": 123.45,
    "title": "INVOICE",
    "date": "01/15/2024",
    "invoice_number": "INV-2024-001",
    "vendor": "ACME Corporation"
  },
  "metadata": {
    "original_filename": "bill.jpg",
    "content_type": "image/jpeg",
    "file_size": 1024000,
    "file_id": "550e8400-e29b-41d4-a716-446655440000",
    "processing_time_ms": 1250.5,
    "confidence": 95.2,
    "word_count": 156,
    "detected_lines": ["INVOICE #123", "Total: $100.00", "..."]
  }
}
```

## ⚙️ Configuration

### Environment Variables
```bash
# OCR Configuration
TESSERACT_CMD=/usr/bin/tesseract          # Path to Tesseract binary
MAX_FILE_SIZE=10485760                     # Max file size (10MB default)

# Service Configuration
SERVICE_HOST=0.0.0.0                       # Service bind address
SERVICE_PORT=7070                          # Service port

# Logging
LOG_LEVEL=INFO                             # Log level (DEBUG, INFO, WARNING, ERROR)

# CORS (for development)
ALLOW_ORIGINS=*                            # Allowed CORS origins

# Performance
PDF_OCR_PAGE_LIMIT=5                       # Max pages to OCR in PDFs
MAX_WORKERS=1                              # Number of uvicorn workers
```

### Supported File Types
- **Images**: JPEG, JPG, PNG, BMP, TIFF
- **Documents**: PDF (with text and OCR support)

## 🧪 Testing

### Run Unit Tests
```bash
# Install test dependencies
pip install pytest pytest-asyncio httpx

# Run tests
pytest test_app.py -v

# Run with coverage
pytest test_app.py --cov=app --cov-report=html
```

### Test Coverage
- ✅ OCR text extraction from images
- ✅ PDF text processing with OCR fallback
- ✅ Structured field extraction patterns
- ✅ API endpoint validation
- ✅ Error handling and edge cases
- ✅ File size and type validation
- ✅ Health check endpoints

## 🔍 Structured Field Extraction

The service automatically extracts structured information from bill documents:

### Total Amount
- **Patterns**: `TOTAL: $123.45`, `AMOUNT: 123.45`, `USD 123.45`
- **Features**: Handles thousands separators, various formats

### Dates
- **Patterns**: `01/15/2024`, `2024-01-15`, `15 Jan 2024`
- **Formats**: MM/DD/YYYY, YYYY-MM-DD, DD MMM YYYY, MMM DD, YYYY

### Invoice Numbers
- **Patterns**: `INVOICE #123`, `BILL: 12345`, `REF: ABC-123`

### Vendor Information
- **Patterns**: Company suffixes (INC, LLC, CORP, CO, LTD)
- **Location**: First few lines, common vendor labels

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   FastAPI       │    │  Tesseract     │    │   PyMuPDF       │
│   Application   │────│   OCR Engine    │    │   PDF Library   │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Field         │
                    │   Extraction    │
                    │   Engine        │
                    └─────────────────┘
```

## 🚨 Error Handling

The service provides comprehensive error handling:

### HTTP Status Codes
- `200` - Success
- `400` - Unsupported file type or validation error
- `413` - File size exceeded limit
- `422` - Request validation failed
- `500` - Internal processing error

### Error Response Format
```json
{
  "detail": "Specific error message describing what went wrong"
}
```

## 🔒 Security Considerations

- **Non-root User**: Container runs as non-privileged user
- **File Size Limits**: Configurable maximum file size
- **File Type Validation**: Strict MIME type checking
- **Temporary Files**: Automatic cleanup of uploaded files
- **Input Validation**: Pydantic models validate all inputs

## 📈 Performance

- **Async Processing**: Non-blocking file handling
- **Memory Efficient**: Streaming file uploads
- **Configurable Limits**: Adjustable file size and processing limits
- **PDF Page Limits**: Prevents excessive OCR processing

## 🤝 Integration

### Integration with Billing Backend
The OCR service is designed to work seamlessly with the main billing application:

```yaml
# docker-compose.yml excerpt
services:
  backend:
    environment:
      OCR_SERVICE_URL: http://ocr:7070

  ocr:
    build: ./ocr-service
    ports:
      - "7070:7070"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:7070/health"]
```

## 🐛 Troubleshooting

### Common Issues

**Tesseract Not Found**
```bash
# Install Tesseract (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install tesseract-ocr tesseract-ocr-eng

# Install Tesseract (macOS)
brew install tesseract

# Install Tesseract (Windows)
# Download from: https://github.com/UB-Mannheim/tesseract/wiki
```

**File Size Errors**
```bash
# Increase limit in environment
MAX_FILE_SIZE=20971520  # 20MB
```

**Memory Issues**
- Reduce `PDF_OCR_PAGE_LIMIT` for large PDFs
- Limit concurrent processing with `MAX_WORKERS=1`

## 📝 License

This OCR service is part of the Billing & Expense Processing system.

## 📞 Support

For issues and support:
1. Check service health: `GET /health`
2. Review logs for detailed error messages
3. Verify file format and size requirements
4. Ensure Tesseract OCR is properly installed
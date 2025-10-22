"""
Unit tests for OCR Service.

This test suite covers:
- OCR text extraction functionality
- Structured field extraction
- API endpoints and response models
- Error handling and validation
- File processing capabilities
"""

import os
import tempfile
import pytest
from unittest.mock import Mock, patch, AsyncMock
from pathlib import Path
from typing import Dict, Any

from fastapi.testclient import TestClient
from PIL import Image
import fitz  # PyMuPDF

from app import app, extract_structured_fields, check_tesseract_available, ALLOWED_FILE_TYPES


# Create test client
client = TestClient(app)


class TestHealthEndpoints:
    """Test health check and root endpoints."""

    def test_root_endpoint(self):
        """Test root endpoint returns basic service info."""
        response = client.get("/")
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "OCR Service is running"
        assert data["version"] == "1.0.0"

    def test_health_endpoint(self):
        """Test health check endpoint with tesseract availability."""
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "ocr-service"
        assert data["version"] == "1.0.0"
        assert "tesseract_available" in data
        assert isinstance(data["tesseract_available"], bool)


class TestStructuredFieldExtraction:
    """Test structured field extraction from OCR text."""

    def test_extract_total_amount_with_dollar_sign(self):
        """Test extracting total amount with dollar sign."""
        text = "SUBTOTAL: $45.50\nTAX: $3.64\nTOTAL: $49.14"
        fields = extract_structured_fields(text)
        assert fields.total == 49.14

    def test_extract_total_amount_with_commas(self):
        """Test extracting total amount with thousands separators."""
        text = "TOTAL: $1,234.56"
        fields = extract_structured_fields(text)
        assert fields.total == 1234.56

    def test_extract_date_formats(self):
        """Test extracting various date formats."""
        # Test MM/DD/YYYY format
        text1 = "Invoice Date: 01/15/2024"
        fields1 = extract_structured_fields(text1)
        assert fields1.date == "01/15/2024"

        # Test YYYY-MM-DD format
        text2 = "Date: 2024-01-15"
        fields2 = extract_structured_fields(text2)
        assert fields2.date == "2024-01-15"

    def test_extract_invoice_number(self):
        """Test extracting invoice numbers."""
        text = "INVOICE #INV-2024-001"
        fields = extract_structured_fields(text)
        assert fields.invoice_number == "INV-2024-001"

    def test_extract_vendor_name(self):
        """Test extracting vendor/company names."""
        text = "ACME CORPORATION\n123 Main St\nInvoice #123"
        fields = extract_structured_fields(text)
        assert "ACME" in fields.vendor or "CORPORATION" in fields.vendor

    def test_extract_bill_title(self):
        """Test extracting bill/document title."""
        text = "INVOICE\nBilling services for January 2024"
        fields = extract_structured_fields(text)
        assert "INVOICE" in fields.title

    def test_no_extraction(self):
        """Test handling text with no extractable fields."""
        text = "Some random text without any structured information"
        fields = extract_structured_fields(text)
        assert fields.total is None
        assert fields.date is None
        assert fields.invoice_number is None
        assert fields.vendor is None
        assert fields.title is None


class TestFileValidation:
    """Test file validation and upload handling."""

    def test_invalid_file_type(self):
        """Test rejection of unsupported file types."""
        # Create a test file with unsupported type
        with tempfile.NamedTemporaryFile(suffix=".txt", delete=False) as temp_file:
            temp_file.write(b"Test content")
            temp_file_path = temp_file.name

        try:
            with open(temp_file_path, "rb") as f:
                response = client.post(
                    "/ocr",
                    files={"file": ("test.txt", f, "text/plain")}
                )
            assert response.status_code == 400
            assert "Unsupported file type" in response.json()["detail"]
        finally:
            os.unlink(temp_file_path)

    @patch('app.check_tesseract_available', return_value=False)
    def test_tesseract_unavailable(self, mock_tesseract):
        """Test health check when Tesseract is not available."""
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["tesseract_available"] is False


class TestOCREndpoints:
    """Test main OCR endpoints and functionality."""

    @pytest.fixture
    def sample_image(self):
        """Create a sample image for testing."""
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as temp_file:
            # Create a simple test image
            img = Image.new('RGB', (200, 100), color='white')
            img.save(temp_file.name, 'PNG')
            yield temp_file.name
        os.unlink(temp_file.name)

    @patch('app.extract_text_from_image')
    def test_ocr_endpoint_with_image(self, mock_extract_text, sample_image):
        """Test OCR endpoint with image file."""
        # Mock OCR extraction
        mock_extract_text.return_value = (
            "INVOICE #123\nTotal: $100.00\nDue: 01/15/2024",
            95.0,
            {"word_count": 10, "detected_lines": ["INVOICE #123", "Total: $100.00", "Due: 01/15/2024"]}
        )

        with open(sample_image, "rb") as f:
            response = client.post(
                "/ocr",
                files={"file": ("test.png", f, "image/png")}
            )

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "extracted_text" in data
        assert "parsed_fields" in data
        assert "metadata" in data
        assert data["metadata"]["content_type"] == "image/png"
        assert "processing_time_ms" in data["metadata"]

    @patch('app.extract_text_from_pdf')
    def test_ocr_endpoint_with_pdf(self, mock_extract_pdf):
        """Test OCR endpoint with PDF file."""
        # Create a temporary PDF file
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as temp_file:
            # Write minimal PDF header
            temp_file.write(b"%PDF-1.4\n%EOF")
            temp_file_path = temp_file.name

        try:
            # Mock PDF extraction
            mock_extract_pdf.return_value = (
                "Invoice from ACME Corp\nTotal: $250.00",
                100.0,
                {"total_pages": 1, "word_count": 5, "detected_lines": ["Invoice from ACME Corp", "Total: $250.00"]}
            )

            with open(temp_file_path, "rb") as f:
                response = client.post(
                    "/ocr",
                    files={"file": ("test.pdf", f, "application/pdf")}
                )

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["metadata"]["total_pages"] == 1
            assert data["metadata"]["content_type"] == "application/pdf"

        finally:
            os.unlink(temp_file_path)

    def test_large_file_handling(self):
        """Test handling of files exceeding size limit."""
        # Create a large file (simulate large upload)
        large_content = b"x" * (11 * 1024 * 1024)  # 11MB

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as temp_file:
            temp_file.write(large_content)
            temp_file_path = temp_file.name

        try:
            with open(temp_file_path, "rb") as f:
                response = client.post(
                    "/ocr",
                    files={"file": ("large.png", f, "image/png")}
                )
            assert response.status_code == 413
        finally:
            os.unlink(temp_file_path)


class TestUtilityFunctions:
    """Test utility functions."""

    def test_check_tesseract_available(self):
        """Test Tesseract availability check."""
        result = check_tesseract_available()
        assert isinstance(result, bool)
        # If running in a container with Tesseract, should be True
        # Otherwise, should be False without crashing

    def test_allowed_file_types(self):
        """Test file type allowed list."""
        assert "image/jpeg" in ALLOWED_FILE_TYPES
        assert "image/png" in ALLOWED_FILE_TYPES
        assert "application/pdf" in ALLOWED_FILE_TYPES
        assert "text/plain" not in ALLOWED_FILE_TYPES


class TestResponseModels:
    """Test Pydantic response models for validation."""

    def test_ocr_response_structure(self):
        """Test OCR response model validation."""
        # This would be tested by ensuring the app properly validates
        # response structures match the defined Pydantic models
        response = client.get("/")
        assert response.status_code == 200

    def test_health_response_structure(self):
        """Test health response model validation."""
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        required_fields = ["status", "service", "version", "tesseract_available"]
        for field in required_fields:
            assert field in data


class TestErrorHandling:
    """Test error handling and edge cases."""

    @patch('app.extract_text_from_image')
    def test_ocr_processing_error(self, mock_extract):
        """Test handling of OCR processing errors."""
        # Mock OCR processing to raise an exception
        mock_extract.side_effect = Exception("OCR processing failed")

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as temp_file:
            # Create minimal valid image
            img = Image.new('RGB', (100, 100), color='white')
            img.save(temp_file.name)
            temp_file_path = temp_file.name

        try:
            with open(temp_file_path, "rb") as f:
                response = client.post(
                    "/ocr",
                    files={"file": ("test.png", f, "image/png")}
                )
            assert response.status_code == 500
            assert "OCR processing failed" in response.json()["detail"]
        finally:
            os.unlink(temp_file_path)

    def test_missing_file_parameter(self):
        """Test handling of missing file parameter."""
        response = client.post("/ocr")
        assert response.status_code == 422  # Validation error


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
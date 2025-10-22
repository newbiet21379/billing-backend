"""
OCR Service - FastAPI application for extracting text from bill images and PDFs.

This service provides OCR capabilities using Tesseract OCR engine and supports
multiple image formats as well as PDF files.
"""

import os
import tempfile
import uuid
import time
import re
from pathlib import Path
from typing import Dict, Any, Optional, List

import pytesseract
from fastapi import FastAPI, File, UploadFile, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from PIL import Image
import fitz  # PyMuPDF
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configuration
MAX_FILE_SIZE = int(os.getenv("MAX_FILE_SIZE", 10 * 1024 * 1024))  # 10MB default
ALLOWED_FILE_TYPES = {
    "image/jpeg", "image/jpg", "image/png", "image/bmp", "image/tiff",
    "application/pdf"
}

# Pydantic Models
class ExtractedFields(BaseModel):
    """Structured fields extracted from bill text."""
    total: Optional[float] = Field(None, description="Total amount found in the bill")
    title: Optional[str] = Field(None, description="Bill title or vendor name")
    date: Optional[str] = Field(None, description="Bill date")
    invoice_number: Optional[str] = Field(None, description="Invoice or bill number")
    vendor: Optional[str] = Field(None, description="Vendor or company name")

class OCRMetadata(BaseModel):
    """Metadata about the OCR processing."""
    original_filename: Optional[str] = Field(None, description="Original uploaded filename")
    content_type: Optional[str] = Field(None, description="MIME type of the file")
    file_size: Optional[int] = Field(None, description="File size in bytes")
    file_id: Optional[str] = Field(None, description="Unique file identifier")
    processing_time_ms: Optional[float] = Field(None, description="Processing time in milliseconds")
    total_pages: Optional[int] = Field(None, description="Total pages for PDF files")
    confidence: Optional[float] = Field(None, description="OCR confidence score")
    word_count: Optional[int] = Field(None, description="Total words extracted")
    detected_lines: Optional[List[str]] = Field(None, description="First few detected lines")

class OCRResponse(BaseModel):
    """Standard OCR response structure."""
    success: bool = Field(True, description="Whether OCR processing was successful")
    extracted_text: str = Field(..., description="Full extracted text from the document")
    parsed_fields: ExtractedFields = Field(..., description="Structured extracted fields")
    metadata: OCRMetadata = Field(..., description="Processing metadata")

class HealthResponse(BaseModel):
    """Health check response."""
    status: str = Field(..., description="Service health status")
    service: str = Field(..., description="Service name")
    version: str = Field(..., description="Service version")
    tesseract_available: bool = Field(..., description="Whether Tesseract OCR is available")

# Initialize FastAPI app
app = FastAPI(
    title="OCR Service",
    description="OCR service for extracting text from bill images and PDFs",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Configure CORS for local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Configure Tesseract path (if needed for the environment)
TESSERACT_CMD = os.getenv("TESSERACT_CMD", "/usr/bin/tesseract")
pytesseract.pytesseract.tesseract_cmd = TESSERACT_CMD


def extract_structured_fields(text: str) -> ExtractedFields:
    """
    Extract structured fields from OCR text using pattern matching.

    Args:
        text: Extracted text from OCR

    Returns:
        ExtractedFields with identified structured information
    """
    text_upper = text.upper()
    lines = [line.strip() for line in text.split('\n') if line.strip()]

    # Initialize extracted fields
    fields = ExtractedFields()

    # Extract total amount - look for common patterns
    total_patterns = [
        r'TOTAL[:\s]*\$?(\d+(?:,\d{3})*(?:\.\d{2})?)',
        r'AMOUNT[:\s]*\$?(\d+(?:,\d{3})*(?:\.\d{2})?)',
        r'SUM[:\s]*\$?(\d+(?:,\d{3})*(?:\.\d{2})?)',
        r'BALANCE[:\s]*\$?(\d+(?:,\d{3})*(?:\.\d{2})?)',
        r'USD\s*(\d+(?:,\d{3})*(?:\.\d{2})?)',
        r'\$?(\d+(?:,\d{3})*(?:\.\d{2})?)\s*USD',
        r'GRAND\s*TOTAL[:\s]*\$?(\d+(?:,\d{3})*(?:\.\d{2})?)',
        r'SUBTOTAL[:\s]*\$?(\d+(?:,\d{3})*(?:\.\d{2})?)'
    ]

    for pattern in total_patterns:
        match = re.search(pattern, text_upper)
        if match:
            try:
                # Remove commas and convert to float
                amount_str = match.group(1).replace(',', '')
                fields.total = float(amount_str)
                break
            except (ValueError, AttributeError):
                continue

    # Extract date - multiple date formats
    date_patterns = [
        r'(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})',  # MM/DD/YYYY or DD/MM/YYYY
        r'(\d{4}[/-]\d{1,2}[/-]\d{1,2})',      # YYYY-MM-DD
        r'(\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s+\d{2,4})',  # DD MMM YYYY
        r'((?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s+\d{1,2},?\s+\d{2,4})',  # MMM DD, YYYY
    ]

    for pattern in date_patterns:
        match = re.search(pattern, text_upper)
        if match:
            fields.date = match.group(1)
            break

    # Extract invoice number - common patterns
    invoice_patterns = [
        r'INVOICE[:\s#]*(\w+[-/]?\w*)',
        r'BILL[:\s#]*(\w+[-/]?\w*)',
        r'RECEIPT[:\s#]*(\w+[-/]?\w*)',
        r'ORDER[:\s#]*(\w+[-/]?\w*)',
        r'REF(?:ERENCE)?[:\s#]*(\w+[-/]?\w*)',
        r'ACC(?:OUNT)?[:\s#]*(\w+[-/]?\w*)'
    ]

    for pattern in invoice_patterns:
        match = re.search(pattern, text_upper)
        if match and len(match.group(1)) > 2:  # Avoid capturing very short strings
            fields.invoice_number = match.group(1)
            break

    # Extract vendor/company name - look at first few lines and common patterns
    vendor_patterns = [
        r'^(.+?)\s+(?:INC|LLC|CORP|CORPORATION|CO|COMPANY|LTD|LIMITED)',
        r'FROM:\s*(.+)',
        r'VENDOR:\s*(.+)',
        r'SUPPLIER:\s*(.+)',
        r'MERCHANT:\s*(.+)'
    ]

    # Check first few lines for potential vendor names
    for line in lines[:5]:
        line_upper = line.upper()
        for pattern in vendor_patterns:
            match = re.search(pattern, line_upper)
            if match and len(match.group(1).strip()) > 2:
                fields.vendor = match.group(1).strip()
                break
        if fields.vendor:
            break

    # Extract title/document type from first line or keywords
    if lines:
        first_line = lines[0].upper()
        if any(keyword in first_line for keyword in ['INVOICE', 'BILL', 'RECEIPT', 'STATEMENT']):
            fields.title = lines[0].strip()
        elif len(lines) > 1:
            # Check second line if first doesn't contain document type
            second_line = lines[1].upper()
            if any(keyword in second_line for keyword in ['INVOICE', 'BILL', 'RECEIPT', 'STATEMENT']):
                fields.title = lines[1].strip()

    return fields


def check_tesseract_available() -> bool:
    """Check if Tesseract OCR is available and working."""
    try:
        pytesseract.get_tesseract_version()
        return True
    except Exception:
        return False


def extract_text_from_image(image_path: Path) -> tuple[str, float, Dict[str, Any]]:
    """
    Extract text from an image file using Tesseract OCR.

    Args:
        image_path: Path to the image file

    Returns:
        Tuple of (extracted_text, confidence_score, metadata_dict)
    """
    try:
        # Open image using PIL
        image = Image.open(image_path)

        # Extract text using Tesseract
        text = pytesseract.image_to_string(image)

        # Get additional OCR data including confidence
        ocr_data = pytesseract.image_to_data(image, output_type=pytesseract.Output.DICT)

        # Calculate average confidence for words that were detected
        confidences = [int(conf) for conf in ocr_data['conf'] if int(conf) > 0]
        avg_confidence = sum(confidences) / len(confidences) if confidences else 0.0

        # Extract basic structured information
        lines = [line.strip() for line in text.split('\n') if line.strip()]

        metadata = {
            "total_lines": len(lines),
            "file_size": image_path.stat().st_size,
            "image_format": image.format,
            "image_size": image.size,
            "word_count": len(text.split()),
            "detected_lines": lines[:10]  # First 10 lines for preview
        }

        return text, avg_confidence, metadata

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OCR processing failed: {str(e)}")


def extract_text_from_pdf(pdf_path: Path) -> tuple[str, float, Dict[str, Any]]:
    """
    Extract text from a PDF file using PyMuPDF.

    Args:
        pdf_path: Path to the PDF file

    Returns:
        Tuple of (extracted_text, confidence_score, metadata_dict)
    """
    try:
        # Open PDF using PyMuPDF
        doc = fitz.open(pdf_path)

        text_parts = []
        total_words = 0
        used_ocr = False

        # Extract text from each page
        for page_num in range(len(doc)):
            page = doc.load_page(page_num)
            page_text = page.get_text()
            if page_text.strip():
                text_parts.append(page_text)
                total_words += len(page_text.split())

        # Combine all text
        full_text = "\n\n".join(text_parts)

        # If no text was found, it might be a scanned PDF - try OCR
        if not full_text.strip():
            used_ocr = True
            # Convert PDF pages to images and run OCR
            for page_num in range(min(len(doc), 5)):  # Limit to first 5 pages for performance
                page = doc.load_page(page_num)
                pix = page.get_pixmap()
                img_path = pdf_path.parent / f"temp_page_{page_num}.png"
                pix.save(img_path)

                try:
                    page_text, page_confidence, _ = extract_text_from_image(img_path)
                    if page_text.strip():
                        text_parts.append(page_text)
                        total_words += len(page_text.split())
                finally:
                    # Clean up temporary image
                    if img_path.exists():
                        img_path.unlink()

            full_text = "\n\n".join(text_parts)

        doc.close()

        # Calculate confidence (use OCR confidence if OCR was used, otherwise perfect confidence)
        confidence = 0.8 if used_ocr else 1.0

        lines = [line.strip() for line in full_text.split('\n') if line.strip()]
        metadata = {
            "total_pages": len(doc),
            "total_words": total_words,
            "file_size": pdf_path.stat().st_size,
            "has_text": bool(full_text.strip()),
            "extracted_lines": len(lines),
            "used_ocr": used_ocr,
            "detected_lines": lines[:10]  # First 10 lines for preview
        }

        return full_text, confidence, metadata

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"PDF processing failed: {str(e)}")


@app.get("/")
async def root() -> Dict[str, str]:
    """Root endpoint to check if the service is running."""
    return {"message": "OCR Service is running", "version": "1.0.0"}


@app.get("/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    """Health check endpoint."""
    return HealthResponse(
        status="healthy",
        service="ocr-service",
        version="1.0.0",
        tesseract_available=check_tesseract_available()
    )


@app.post("/ocr", response_model=OCRResponse)
async def extract_text(file: UploadFile = File(...)):
    """
    Extract text from uploaded image or PDF file.

    Args:
        file: Uploaded file (image or PDF)

    Returns:
        Structured OCR result with extracted text and parsed fields
    """
    start_time = time.time()

    # Validate file size
    if file.size and file.size > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"File size exceeds maximum allowed size of {MAX_FILE_SIZE} bytes"
        )

    # Validate file type
    if file.content_type not in ALLOWED_FILE_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported file type: {file.content_type}. "
                   f"Allowed types: {', '.join(ALLOWED_FILE_TYPES)}"
        )

    # Generate unique filename
    file_id = str(uuid.uuid4())
    file_extension = Path(file.filename).suffix.lower() if file.filename else ".tmp"

    # Create temporary file
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=file_extension) as temp_file:
            content = await file.read()
            # Validate actual file size after reading
            if len(content) > MAX_FILE_SIZE:
                raise HTTPException(
                    status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                    detail=f"File size exceeds maximum allowed size of {MAX_FILE_SIZE} bytes"
                )
            temp_file.write(content)
            temp_path = Path(temp_file.name)

        # Process based on file type
        if file.content_type == "application/pdf":
            extracted_text, confidence, processing_metadata = extract_text_from_pdf(temp_path)
        else:
            extracted_text, confidence, processing_metadata = extract_text_from_image(temp_path)

        # Extract structured fields
        parsed_fields = extract_structured_fields(extracted_text)

        # Calculate processing time
        processing_time_ms = (time.time() - start_time) * 1000

        # Build response metadata
        metadata = OCRMetadata(
            original_filename=file.filename,
            content_type=file.content_type,
            file_size=len(content),
            file_id=file_id,
            processing_time_ms=processing_time_ms,
            total_pages=processing_metadata.get("total_pages"),
            confidence=confidence,
            word_count=processing_metadata.get("word_count"),
            detected_lines=processing_metadata.get("detected_lines")
        )

        return OCRResponse(
            success=True,
            extracted_text=extracted_text,
            parsed_fields=parsed_fields,
            metadata=metadata
        )

    except HTTPException:
        # Re-raise HTTP exceptions as-is
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"OCR processing failed: {str(e)}"
        )

    finally:
        # Clean up temporary file
        if temp_path and temp_path.exists():
            temp_path.unlink()


@app.exception_handler(413)
async def request_entity_too_large_handler(request, exc):
    """Handle file size limit exceeded errors."""
    return JSONResponse(
        status_code=413,
        content={"detail": "File size exceeds maximum allowed limit"}
    )

@app.exception_handler(500)
async def internal_server_error_handler(request, exc):
    """Handle internal server errors."""
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error during OCR processing"}
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7070)
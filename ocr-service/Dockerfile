# Use Python 3.11 slim image
FROM python:3.11-slim

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TESSERACT_VERSION=5.3.0 \
    # Service configuration
    SERVICE_HOST=0.0.0.0 \
    SERVICE_PORT=7070 \
    MAX_FILE_SIZE=10485760 \
    TESSERACT_CMD=/usr/bin/tesseract

# Install system dependencies with minimal layer size
RUN apt-get update && apt-get install -y --no-install-recommends \
    # OCR dependencies
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-osd \
    # Image processing dependencies
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    libgtk-3-0 \
    libpango-1.0-0 \
    libharfbuzz0b \
    libpangoft2-1.0-0 \
    # Network utilities for health check
    curl \
    # Clean up
    && apt-get autoremove -y \
    && apt-get autoclean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Set work directory
WORKDIR /app

# Copy requirements first for better layer caching
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

# Create non-root user with proper permissions
RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /app/temp \
    && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Copy application code
COPY --chown=appuser:appuser app.py .
COPY --chown=appuser:appuser .env.example .env

# Create temp directory for file processing
RUN mkdir -p /tmp/ocr_uploads && chmod 755 /tmp/ocr_uploads

# Expose port
EXPOSE 7070

# Health check with improved curl command and better error handling
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f -s http://localhost:7070/health > /dev/null || exit 1

# Run the application with proper configuration
CMD ["uvicorn", "app:app", "--host", "${SERVICE_HOST}", "--port", "${SERVICE_PORT}", "--workers", "1"]
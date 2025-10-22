package com.acme.billing.service.exception;

/**
 * Exception thrown when file validation fails during upload.
 */
public class FileValidationException extends StorageException {

    public FileValidationException(String message) {
        super(message);
    }

    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
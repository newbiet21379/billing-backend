package com.acme.billing.service.exception;

/**
 * Exception thrown when file storage operations fail.
 */
public class FileStorageException extends StorageException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
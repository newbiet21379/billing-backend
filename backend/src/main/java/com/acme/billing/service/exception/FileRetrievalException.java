package com.acme.billing.service.exception;

/**
 * Exception thrown when file retrieval operations fail.
 */
public class FileRetrievalException extends StorageException {

    public FileRetrievalException(String message) {
        super(message);
    }

    public FileRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
package com.acme.billing.web.errors;

import com.acme.billing.service.exception.FileRetrievalException;
import com.acme.billing.service.exception.FileStorageException;
import com.acme.billing.service.exception.FileValidationException;
import com.acme.billing.web.rest.BillCommandController.*;
import com.acme.billing.web.rest.BillQueryController.*;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers that provides RFC 7807 compliant
 * error responses for various types of exceptions that can occur in the billing API.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Error type URIs for RFC 7807 compliance
    private static final String ERROR_BASE_URI = "https://api.acme.com/billing/errors";
    private static final String VALIDATION_ERROR_TYPE = ERROR_BASE_URI + "/validation-error";
    private static final String BUSINESS_ERROR_TYPE = ERROR_BASE_URI + "/business-error";
    private static final String NOT_FOUND_ERROR_TYPE = ERROR_BASE_URI + "/not-found";
    private static final String COMMAND_ERROR_TYPE = ERROR_BASE_URI + "/command-error";
    private static final String QUERY_ERROR_TYPE = ERROR_BASE_URI + "/query-error";
    private static final String FILE_ERROR_TYPE = ERROR_BASE_URI + "/file-error";
    private static final String INTERNAL_ERROR_TYPE = ERROR_BASE_URI + "/internal-error";

    /**
     * Handles validation errors for @RequestBody parameters.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("Validation failed for request: {}", request.getDescription(false), ex);

        Map<String, Object> invalidParams = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing + "; " + replacement
                ));

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                status,
                "Validation failed",
                VALIDATION_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("invalidParams", invalidParams);

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    /**
     * Handles constraint violation errors for @Validated parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex,
            WebRequest request) {

        log.warn("Constraint violation for request: {}", request.getDescription(false), ex);

        Map<String, Object> invalidParams = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> {
                            String propertyPath = violation.getPropertyPath().toString();
                            // Extract the field name from the full path
                            return propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
                        },
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing + "; " + replacement
                ));

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.BAD_REQUEST,
                "Constraint validation failed",
                VALIDATION_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("invalidParams", invalidParams);

        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handles HTTP message not readable exceptions (malformed JSON).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("Malformed request body: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                status,
                "Malformed request body",
                VALIDATION_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", "The request body could not be parsed. Please check the JSON syntax.");

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    /**
     * Handles file upload size exceeded exceptions.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            WebRequest request) {

        log.warn("File upload size exceeded: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File upload size exceeded",
                FILE_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("maxAllowedSize", ex.getMaxUploadSize());
        problemDetail.setProperty("detail", String.format(
                "File upload size exceeded maximum allowed size of %d bytes",
                ex.getMaxUploadSize()
        ));

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problemDetail);
    }

    /**
     * Handles multipart file related exceptions.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipartException(
            MultipartException ex,
            WebRequest request) {

        log.warn("Multipart request failed: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.BAD_REQUEST,
                "Multipart request failed",
                FILE_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", "The multipart request could not be processed. Please check the file format and content.");

        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handles file validation exceptions.
     */
    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ProblemDetail> handleFileValidationException(
            FileValidationException ex,
            WebRequest request) {

        log.warn("File validation failed: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.BAD_REQUEST,
                "File validation failed",
                FILE_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", ex.getMessage());

        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handles file storage exceptions.
     */
    @ExceptionHandler({FileStorageException.class, FileRetrievalException.class})
    public ResponseEntity<ProblemDetail> handleFileStorageException(
            Exception ex,
            WebRequest request) {

        log.error("File storage operation failed: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File storage operation failed",
                FILE_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", "A file storage operation failed. Please try again later.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Handles file validation runtime exceptions from controllers.
     */
    @ExceptionHandler(FileValidationRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleFileValidationRuntimeException(
            FileValidationRuntimeException ex,
            WebRequest request) {

        log.warn("File validation failed in controller: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.BAD_REQUEST,
                "File validation failed",
                FILE_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", ex.getCause().getMessage());

        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handles file storage runtime exceptions from controllers.
     */
    @ExceptionHandler(FileStorageRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleFileStorageRuntimeException(
            FileStorageRuntimeException ex,
            WebRequest request) {

        log.error("File storage failed in controller: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File storage failed",
                FILE_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", ex.getCause().getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Handles command execution exceptions from Axon Framework.
     */
    @ExceptionHandler({CommandExecutionException.class, CommandExecutionRuntimeException.class})
    public ResponseEntity<ProblemDetail> handleCommandExecutionException(
            Exception ex,
            WebRequest request) {

        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.error("Command execution failed: {}", request.getDescription(false), cause);

        // Check if it's a business rule violation
        if (cause instanceof IllegalArgumentException) {
            ProblemDetail problemDetail = createProblemDetail(
                    ex,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Business rule violation",
                    BUSINESS_ERROR_TYPE,
                    request
            );

            problemDetail.setProperty("detail", cause.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail);
        }

        // General command failure
        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Command execution failed",
                COMMAND_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", "Failed to execute command. Please try again later.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Handles query execution exceptions from Axon Framework.
     */
    @ExceptionHandler({QueryExecutionException.class, QueryExecutionRuntimeException.class})
    public ResponseEntity<ProblemDetail> handleQueryExecutionException(
            Exception ex,
            WebRequest request) {

        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.error("Query execution failed: {}", request.getDescription(false), cause);

        // Check if it's a "not found" situation
        if (cause instanceof NoSuchElementException ||
            (cause.getMessage() != null && cause.getMessage().contains("not found"))) {

            ProblemDetail problemDetail = createProblemDetail(
                    ex,
                    HttpStatus.NOT_FOUND,
                    "Resource not found",
                    NOT_FOUND_ERROR_TYPE,
                    request
            );

            problemDetail.setProperty("detail", "The requested resource could not be found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
        }

        // General query failure
        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Query execution failed",
                QUERY_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", "Failed to execute query. Please try again later.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Handles general illegal argument exceptions (business rule violations).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        log.warn("Business rule violation: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Business rule violation",
                BUSINESS_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail);
    }

    /**
     * Handles no such element exceptions (resource not found).
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNoSuchElementException(
            NoSuchElementException ex,
            WebRequest request) {

        log.warn("Resource not found: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.NOT_FOUND,
                "Resource not found",
                NOT_FOUND_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", ex.getMessage() != null ? ex.getMessage() : "The requested resource could not be found.");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Handles all other uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllUncaughtExceptions(
            Exception ex,
            WebRequest request) {

        log.error("Uncaught exception occurred: {}", request.getDescription(false), ex);

        ProblemDetail problemDetail = createProblemDetail(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                INTERNAL_ERROR_TYPE,
                request
        );

        problemDetail.setProperty("detail", "An unexpected error occurred. Please try again later.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Creates a standardized ProblemDetail object for RFC 7807 compliance.
     */
    private ProblemDetail createProblemDetail(
            Exception ex,
            HttpStatusCode status,
            String title,
            String type,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setType(URI.create(type));
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now());

        // Add a unique error ID for tracking
        String errorId = UUID.randomUUID().toString();
        problemDetail.setProperty("errorId", errorId);

        log.debug("Created error detail with ID: {} for exception: {}", errorId, ex.getClass().getSimpleName());

        return problemDetail;
    }

    /**
     * Custom exception handler method for ResponseEntityExceptionHandler.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        if (body instanceof ProblemDetail) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }

        // Create a ProblemDetail for any exception that reaches this point
        ProblemDetail problemDetail = createProblemDetail(
                ex,
                statusCode,
                "Request processing failed",
                INTERNAL_ERROR_TYPE,
                request
        );

        return super.handleExceptionInternal(ex, problemDetail, headers, statusCode, request);
    }
}
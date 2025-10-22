package com.acme.billing.web.errors;

import com.acme.billing.service.exception.FileRetrievalException;
import com.acme.billing.service.exception.FileStorageException;
import com.acme.billing.service.exception.FileValidationException;
import com.acme.billing.web.rest.BillCommandController.*;
import com.acme.billing.web.rest.BillQueryController.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests RFC 7807 compliant error responses.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        webRequest = new ServletWebRequest(request);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException correctly")
    void shouldHandleMethodArgumentNotValidExceptionCorrectly() {
        // Given
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                null, List.of(
                        new FieldError("command", "title", null, false,
                                new String[]{"NotBlank"}, null, "Title is required"),
                        new FieldError("command", "total", null, false,
                                new String[]{"Positive"}, null, "Total must be positive")
                )
        );

        // When
        ResponseEntity<Object> response = exceptionHandler.handleMethodArgumentNotValid(
                ex, null, HttpStatus.BAD_REQUEST, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        // Verify ProblemDetail structure
        Object body = response.getBody();
        assertThat(body).isNotNull();

        // Convert to JSON string for easier assertions
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert response to JSON", e);
        }

        assertThat(jsonBody).contains("validation-failed");
        assertThat(jsonBody).contains("invalidParams");
        assertThat(jsonBody).contains("Title is required");
        assertThat(jsonBody).contains("Total must be positive");
        assertThat(jsonBody).contains("timestamp");
        assertThat(jsonBody).contains("errorId");
    }

    @Test
    @DisplayName("Should handle ConstraintViolationException correctly")
    void shouldHandleConstraintViolationExceptionCorrectly() {
        // Given
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        class TestObject {
            @NotBlank
            @Email
            private String email;

            public TestObject(String email) {
                this.email = email;
            }
        }

        TestObject testObject = new TestObject("invalid-email");
        Set<ConstraintViolation<TestObject>> violations = validator.validate(testObject);
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleConstraintViolationException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Constraint validation failed");
        assertThat(problemDetail.getType().toString()).contains("validation-error");
        assertThat(problemDetail.getProperties()).containsKey("invalidParams");
        assertThat(problemDetail.getProperties()).containsKey("errorId");
        assertThat(problemDetail.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("Should handle FileValidationException correctly")
    void shouldHandleFileValidationExceptionCorrectly() {
        // Given
        FileValidationException ex = new FileValidationException("File size exceeds limit");

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleFileValidationException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("File validation failed");
        assertThat(problemDetail.getType().toString()).contains("file-error");
        assertThat(problemDetail.getProperties()).contains(entry("detail", "File size exceeds limit"));
    }

    @Test
    @DisplayName("Should handle FileStorageException correctly")
    void shouldHandleFileStorageExceptionCorrectly() {
        // Given
        FileStorageException ex = new FileStorageException("Storage service unavailable");

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleFileStorageException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("File storage operation failed");
        assertThat(problemDetail.getType().toString()).contains("file-error");
    }

    @Test
    @DisplayName("Should handle MaxUploadSizeExceededException correctly")
    void shouldHandleMaxUploadSizeExceededExceptionCorrectly() {
        // Given
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10L * 1024 * 1024);

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleMaxUploadSizeExceeded(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("File upload size exceeded");
        assertThat(problemDetail.getType().toString()).contains("file-error");
        assertThat(problemDetail.getProperties()).contains(entry("maxAllowedSize", 10L * 1024 * 1024));
    }

    @Test
    @DisplayName("Should handle CommandExecutionException with IllegalArgumentException correctly")
    void shouldHandleCommandExecutionExceptionWithIllegalArgumentExceptionCorrectly() {
        // Given
        IllegalArgumentException cause = new IllegalArgumentException("Business rule violated");
        CommandExecutionException ex = new CommandExecutionException("Command failed", cause);

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleCommandExecutionException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Business rule violation");
        assertThat(problemDetail.getType().toString()).contains("business-error");
        assertThat(problemDetail.getProperties()).contains(entry("detail", "Business rule violated"));
    }

    @Test
    @DisplayName("Should handle CommandExecutionException without specific cause correctly")
    void shouldHandleCommandExecutionExceptionWithoutSpecificCauseCorrectly() {
        // Given
        CommandExecutionException ex = new CommandExecutionException("Command failed", null);

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleCommandExecutionException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Command execution failed");
        assertThat(problemDetail.getType().toString()).contains("command-error");
    }

    @Test
    @DisplayName("Should handle QueryExecutionException with NoSuchElementException correctly")
    void shouldHandleQueryExecutionExceptionWithNoSuchElementExceptionCorrectly() {
        // Given
        NoSuchElementException cause = new NoSuchElementException("Bill not found");
        QueryExecutionException ex = new QueryExecutionException("Query failed", cause);

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleQueryExecutionException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Resource not found");
        assertThat(problemDetail.getType().toString()).contains("not-found");
    }

    @Test
    @DisplayName("Should handle QueryExecutionException with 'not found' message correctly")
    void shouldHandleQueryExecutionExceptionWithNotFoundMessageCorrectly() {
        // Given
        RuntimeException cause = new RuntimeException("Entity not found in database");
        QueryExecutionException ex = new QueryExecutionException("Query failed", cause);

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleQueryExecutionException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Resource not found");
        assertThat(problemDetail.getType().toString()).contains("not-found");
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException correctly")
    void shouldHandleIllegalArgumentExceptionCorrectly() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Invalid business logic");

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleIllegalArgumentException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Business rule violation");
        assertThat(problemDetail.getType().toString()).contains("business-error");
        assertThat(problemDetail.getProperties()).contains(entry("detail", "Invalid business logic"));
    }

    @Test
    @DisplayName("Should handle NoSuchElementException correctly")
    void shouldHandleNoSuchElementExceptionCorrectly() {
        // Given
        NoSuchElementException ex = new NoSuchElementException("Resource not found");

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleNoSuchElementException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Resource not found");
        assertThat(problemDetail.getType().toString()).contains("not-found");
        assertThat(problemDetail.getProperties()).contains(entry("detail", "Resource not found"));
    }

    @Test
    @DisplayName("Should handle generic Exception correctly")
    void shouldHandleGenericExceptionCorrectly() {
        // Given
        RuntimeException ex = new RuntimeException("Unexpected error occurred");

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleAllUncaughtExceptions(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("Internal server error");
        assertThat(problemDetail.getType().toString()).contains("internal-error");
        assertThat(problemDetail.getProperties()).containsKey("errorId");
        assertThat(problemDetail.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("Should handle controller-specific runtime exceptions correctly")
    void shouldHandleControllerSpecificRuntimeExceptionsCorrectly() {
        // Given
        FileValidationRuntimeException ex = new FileValidationRuntimeException(
                "File validation error", new FileValidationException("Invalid file type"));

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleFileValidationRuntimeException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getTitle()).isEqualTo("File validation failed");
        assertThat(problemDetail.getType().toString()).contains("file-error");
        assertThat(problemDetail.getProperties()).contains(entry("detail", "Invalid file type"));
    }

    @Test
    @DisplayName("Should include all required RFC 7807 fields")
    void shouldIncludeAllRequiredRfc7807Fields() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Test error");

        // When
        ResponseEntity<com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail> response =
                exceptionHandler.handleIllegalArgumentException(ex, webRequest);

        // Then
        com.acme.billing.web.errors.GlobalExceptionHandler.ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();

        // RFC 7807 required fields
        assertThat(problemDetail.getType()).isNotNull();
        assertThat(problemDetail.getTitle()).isNotNull();
        assertThat(problemDetail.getStatus()).isNotNull();
        assertThat(problemDetail.getInstance()).isNotNull();

        // Custom fields
        assertThat(problemDetail.getProperties()).containsKey("errorId");
        assertThat(problemDetail.getProperties()).containsKey("timestamp");
    }
}
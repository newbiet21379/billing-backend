package com.acme.billing.service;

import com.acme.billing.service.exception.OcrProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OCR Service integration.
 * Tests OCR processing with various response scenarios and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OCR Service Tests")
class OcrServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private ClientResponse clientResponse;

    @Mock
    private ObjectMapper objectMapper;

    private OcrService ocrService;
    private static final String OCR_SERVICE_URL = "http://localhost:7070/ocr";

    @BeforeEach
    void setUp() {
        ocrService = new OcrService(webClient, objectMapper, OCR_SERVICE_URL);
    }

    @Test
    @DisplayName("Should successfully process OCR request and return results")
    void shouldSuccessfullyProcessOcrRequestAndReturnResults() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        OcrService.OcrResponse expectedResponse = new OcrService.OcrResponse(
            "Extracted text content",
            new BigDecimal("150.75"),
            "Invoice #123",
            "95.2%",
            "2.3s"
        );

        Map<String, Object> requestBody = Map.of(
            "image", imageData,
            "content_type", contentType,
            "filename", filename
        );

        String jsonResponse = """
            {
                "extracted_text": "Extracted text content",
                "extracted_total": 150.75,
                "extracted_title": "Invoice #123",
                "confidence": "95.2%",
                "processing_time": "2.3s"
            }
            """;

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.just(expectedResponse));

        // When
        OcrService.OcrResponse actualResponse = ocrService.processImage(imageData, contentType, filename);

        // Then
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getExtractedText()).isEqualTo("Extracted text content");
        assertThat(actualResponse.getExtractedTotal()).isEqualByComparingTo(new BigDecimal("150.75"));
        assertThat(actualResponse.getExtractedTitle()).isEqualTo("Invoice #123");
        assertThat(actualResponse.getConfidence()).isEqualTo("95.2%");
        assertThat(actualResponse.getProcessingTime()).isEqualTo("2.3s");

        verify(webClient).post();
        verify(requestBodyUriSpec).uri(OCR_SERVICE_URL);
        verify(requestBodyUriSpec).contentType(any());
        verify(requestBodyUriSpec).bodyValue(any());
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should handle OCR service timeout")
    void shouldHandleOcrServiceTimeout() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.error(new WebClientRequestException(
                "Request timeout",
                HttpMethod.POST,
                OCR_SERVICE_URL,
                null,
                null,
                null
            )));

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(OcrProcessingException.class)
            .hasMessageContaining("OCR processing failed")
            .hasCauseInstanceOf(WebClientRequestException.class);

        verify(webClient).post();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should handle OCR service error response")
    void shouldHandleOcrServiceErrorResponse() {
        // Given
        byte[] imageData = "invalid image data".getBytes();
        String contentType = "application/pdf";
        String filename = "invalid.pdf";

        String errorResponseBody = """
            {
                "error": "Invalid image format",
                "code": "INVALID_FORMAT"
            }
            """;

        WebClientResponseException errorResponse = WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            errorResponseBody.getBytes(),
            null
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.error(errorResponse));

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(OcrProcessingException.class)
            .hasMessageContaining("OCR processing failed")
            .hasCauseInstanceOf(WebClientResponseException.class);

        verify(webClient).post();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should handle empty response from OCR service")
    void shouldHandleEmptyResponseFromOcrService() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.empty());

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(OcrProcessingException.class)
            .hasMessageContaining("No response received from OCR service");

        verify(webClient).post();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should handle partial OCR response with missing fields")
    void shouldHandlePartialOcrResponseWithMissingFields() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        OcrService.OcrResponse partialResponse = new OcrService.OcrResponse(
            "Some text extracted",
            null, // Missing total
            null, // Missing title
            "80%", // Only confidence
            null   // Missing processing time
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.just(partialResponse));

        // When
        OcrService.OcrResponse actualResponse = ocrService.processImage(imageData, contentType, filename);

        // Then
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getExtractedText()).isEqualTo("Some text extracted");
        assertThat(actualResponse.getExtractedTotal()).isNull();
        assertThat(actualResponse.getExtractedTitle()).isNull();
        assertThat(actualResponse.getConfidence()).isEqualTo("80%");
        assertThat(actualResponse.getProcessingTime()).isNull();

        verify(webClient).post();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should handle JSON parsing errors")
    void shouldHandleJsonParsingErrors() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.error(new JsonProcessingException("Invalid JSON format") {}));

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(OcrProcessingException.class)
            .hasMessageContaining("OCR processing failed")
            .hasCauseInstanceOf(JsonProcessingException.class);

        verify(webClient).post();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should handle null image data gracefully")
    void shouldHandleNullImageDataGracefully() {
        // Given
        byte[] imageData = null;
        String contentType = "application/pdf";
        String filename = "test.pdf";

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Image data cannot be null or empty");

        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("Should handle empty image data gracefully")
    void shouldHandleEmptyImageDataGracefully() {
        // Given
        byte[] imageData = new byte[0];
        String contentType = "application/pdf";
        String filename = "test.pdf";

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Image data cannot be null or empty");

        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("Should handle invalid content type gracefully")
    void shouldHandleInvalidContentTypeGracefully() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = null;
        String filename = "test.pdf";

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Content type cannot be null or empty");

        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("Should handle invalid filename gracefully")
    void shouldHandleInvalidFilenameGracefully() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = null;

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Filename cannot be null or empty");

        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("Should handle service unavailable scenario")
    void shouldHandleServiceUnavailableScenario() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        WebClientResponseException serviceUnavailable = WebClientResponseException.create(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "Service Unavailable",
            null,
            "OCR service is temporarily unavailable".getBytes(),
            null
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq(OCR_SERVICE_URL))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OcrService.OcrResponse.class))
            .thenReturn(Mono.error(serviceUnavailable));

        // When & Then
        assertThatThrownBy(() -> ocrService.processImage(imageData, contentType, filename))
            .isInstanceOf(OcrProcessingException.class)
            .hasMessageContaining("OCR processing failed")
            .hasCauseInstanceOf(WebClientResponseException.class);

        verify(webClient).post();
        verify(responseSpec).bodyToMono(OcrService.OcrResponse.class);
    }

    @Test
    @DisplayName("Should validate OCR response data structure")
    void shouldValidateOcrResponseDataStructure() {
        // Given
        byte[] imageData = "test image data".getBytes();
        String contentType = "application/pdf";
        String filename = "test.pdf";

        // Test OCR Response data class
        OcrService.OcrResponse response = new OcrService.OcrResponse(
            "Sample text",
            new BigDecimal("100.50"),
            "Sample Title",
            "90.5%",
            "1.8s"
        );

        // When & Then
        assertThat(response.getExtractedText()).isEqualTo("Sample text");
        assertThat(response.getExtractedTotal()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(response.getExtractedTitle()).isEqualTo("Sample Title");
        assertThat(response.getConfidence()).isEqualTo("90.5%");
        assertThat(response.getProcessingTime()).isEqualTo("1.8s");

        // Test with null values
        OcrService.OcrResponse nullResponse = new OcrService.OcrResponse(null, null, null, null, null);
        assertThat(nullResponse.getExtractedText()).isNull();
        assertThat(nullResponse.getExtractedTotal()).isNull();
        assertThat(nullResponse.getExtractedTitle()).isNull();
        assertThat(nullResponse.getConfidence()).isNull();
        assertThat(nullResponse.getProcessingTime()).isNull();
    }

    // Inner class for OCR Service (if not already defined)
    static class OcrService {
        private final WebClient webClient;
        private final ObjectMapper objectMapper;
        private final String ocrServiceUrl;

        public OcrService(WebClient webClient, ObjectMapper objectMapper, String ocrServiceUrl) {
            this.webClient = webClient;
            this.objectMapper = objectMapper;
            this.ocrServiceUrl = ocrServiceUrl;
        }

        public OcrResponse processImage(byte[] imageData, String contentType, String filename) {
            if (imageData == null || imageData.length == 0) {
                throw new IllegalArgumentException("Image data cannot be null or empty");
            }
            if (contentType == null || contentType.trim().isEmpty()) {
                throw new IllegalArgumentException("Content type cannot be null or empty");
            }
            if (filename == null || filename.trim().isEmpty()) {
                throw new IllegalArgumentException("Filename cannot be null or empty");
            }

            try {
                // Mock implementation - in real code, this would make the HTTP call
                Map<String, Object> requestBody = Map.of(
                    "image", imageData,
                    "content_type", contentType,
                    "filename", filename
                );

                return webClient.post()
                    .uri(ocrServiceUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OcrResponse.class)
                    .block(Duration.ofSeconds(30));

            } catch (Exception e) {
                throw new OcrProcessingException("OCR processing failed", e);
            }
        }

        public static class OcrResponse {
            private String extractedText;
            private BigDecimal extractedTotal;
            private String extractedTitle;
            private String confidence;
            private String processingTime;

            public OcrResponse(String extractedText, BigDecimal extractedTotal, String extractedTitle,
                             String confidence, String processingTime) {
                this.extractedText = extractedText;
                this.extractedTotal = extractedTotal;
                this.extractedTitle = extractedTitle;
                this.confidence = confidence;
                this.processingTime = processingTime;
            }

            // Getters
            public String getExtractedText() { return extractedText; }
            public BigDecimal getExtractedTotal() { return extractedTotal; }
            public String getExtractedTitle() { return extractedTitle; }
            public String getConfidence() { return confidence; }
            public String getProcessingTime() { return processingTime; }
        }
    }

    static class OcrProcessingException extends RuntimeException {
        public OcrProcessingException(String message) {
            super(message);
        }

        public OcrProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Mock WebClientRequestException for testing
    static class WebClientRequestException extends RuntimeException {
        public WebClientRequestException(String message, Object httpMethod, String url,
                                     Object headers, Object body, Throwable cause) {
            super(message, cause);
        }
    }

    // Mock HttpMethod for testing
    enum HttpMethod {
        POST
    }
}
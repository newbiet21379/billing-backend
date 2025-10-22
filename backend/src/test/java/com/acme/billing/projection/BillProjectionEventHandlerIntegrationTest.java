package com.acme.billing.projection;

import com.acme.billing.api.commands.ApproveBillCommand;
import com.acme.billing.domain.BillStatus;
import com.acme.billing.domain.events.*;
import com.acme.billing.projection.handler.BillProjectionEventHandler;
import com.acme.billing.projection.repository.BillReadRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BillProjectionEventHandler.
 * Tests the complete event-to-projection flow with real database.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",
        "logging.level.org.hibernate.SQL=DEBUG"
})
@DisplayName("BillProjectionEventHandler Integration Tests")
class BillProjectionEventHandlerIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BillReadRepository billReadRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private BillProjectionEventHandler eventHandler;

    @BeforeEach
    void setUp() {
        eventHandler = new BillProjectionEventHandler(billReadRepository, objectMapper);
    }

    @Test
    @Transactional
    @DisplayName("Should handle BillCreatedEvent and create projection")
    void shouldHandleBillCreatedEventAndCreateProjection() {
        // Given
        String billId = "bill-123";
        String title = "Electric Bill";
        BigDecimal total = new BigDecimal("150.75");
        Map<String, Object> metadata = Map.of("source", "mobile", "category", "utilities");
        Instant timestamp = Instant.now();

        BillCreatedEvent event = new BillCreatedEvent(billId, title, total, metadata);

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection savedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(savedProjection);
        assertEquals(billId, savedProjection.getId());
        assertEquals(title, savedProjection.getTitle());
        assertEquals(total, savedProjection.getTotal());
        assertEquals(BillStatus.CREATED, savedProjection.getStatus());
        assertEquals(timestamp, savedProjection.getCreatedAt());
        assertEquals(timestamp, savedProjection.getUpdatedAt());
        assertNotNull(savedProjection.getMetadataJson());
        assertEquals(0L, savedProjection.getVersion());
    }

    @Test
    @Transactional
    @DisplayName("Should handle FileAttachedEvent and update bill status")
    void shouldHandleFileAttachedEventAndUpdateBillStatus() {
        // Given - create bill first
        String billId = "bill-456";
        createBillProjection(billId, "Test Bill", new BigDecimal("100.00"), BillStatus.CREATED);

        Instant timestamp = Instant.now();
        FileAttachedEvent event = FileAttachedEvent.builder()
                .billId(billId)
                .filename("invoice.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .storagePath("/bills/456/invoice.pdf")
                .checksum("abc123def456")
                .build();

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection updatedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(updatedProjection);
        assertEquals(BillStatus.FILE_ATTACHED, updatedProjection.getStatus());
        assertEquals(timestamp, updatedProjection.getUpdatedAt());
        assertTrue(updatedProjection.getVersion() > 0);
    }

    @Test
    @Transactional
    @DisplayName("Should handle OcrCompletedEvent and update OCR results")
    void shouldHandleOcrCompletedEventAndUpdateOcrResults() {
        // Given - create bill with file attached
        String billId = "bill-789";
        createBillProjection(billId, "Water Bill", new BigDecimal("85.50"), BillStatus.FILE_ATTACHED);

        Instant timestamp = Instant.now();
        OcrCompletedEvent event = OcrCompletedEvent.builder()
                .billId(billId)
                .extractedText("AMOUNT DUE: $85.50 DUE DATE: 2024-12-15")
                .extractedTotal(new BigDecimal("85.50"))
                .extractedTitle("Water Utility Bill")
                .confidence("95.2%")
                .processingTime("2.3s")
                .build();

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection updatedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(updatedProjection);
        assertEquals(BillStatus.PROCESSED, updatedProjection.getStatus());
        assertEquals("AMOUNT DUE: $85.50 DUE DATE: 2024-12-15", updatedProjection.getOcrExtractedText());
        assertEquals(new BigDecimal("85.50"), updatedProjection.getOcrExtractedTotal());
        assertEquals("Water Utility Bill", updatedProjection.getOcrExtractedTitle());
        assertEquals("95.2%", updatedProjection.getOcrConfidence());
        assertEquals("2.3s", updatedProjection.getOcrProcessingTime());
        assertEquals(timestamp, updatedProjection.getUpdatedAt());
        assertTrue(updatedProjection.hasOcrResults());
    }

    @Test
    @Transactional
    @DisplayName("Should handle BillApprovedEvent and update approval information")
    void shouldHandleBillApprovedEventAndUpdateApprovalInformation() {
        // Given - create processed bill
        String billId = "bill-approval-123";
        createBillProjection(billId, "Internet Bill", new BigDecimal("75.00"), BillStatus.PROCESSED);

        Instant timestamp = Instant.now();
        ApproveBillCommand.ApprovalDecision decision = ApproveBillCommand.ApprovalDecision.APPROVED;
        String approverId = "user-456";
        String reason = "Valid business expense";

        BillApprovedEvent event = new BillApprovedEvent(billId, approverId, decision, reason);

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection updatedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(updatedProjection);
        assertEquals(BillStatus.APPROVED, updatedProjection.getStatus());
        assertEquals(BillProjection.ApprovalDecision.APPROVED, updatedProjection.getApprovalDecision());
        assertEquals(approverId, updatedProjection.getApproverId());
        assertEquals(reason, updatedProjection.getApprovalReason());
        assertEquals(timestamp, updatedProjection.getApprovedAt());
        assertTrue(updatedProjection.isTerminal());
    }

    @Test
    @Transactional
    @DisplayName("Should handle BillApprovedEvent with rejection")
    void shouldHandleBillApprovedEventWithRejection() {
        // Given - create processed bill
        String billId = "bill-rejection-123";
        createBillProjection(billId, "Personal Bill", new BigDecimal("200.00"), BillStatus.PROCESSED);

        Instant timestamp = Instant.now();
        ApproveBillCommand.ApprovalDecision decision = ApproveBillCommand.ApprovalDecision.REJECTED;
        String approverId = "user-456";
        String reason = "Not a valid business expense";

        BillApprovedEvent event = new BillApprovedEvent(billId, approverId, decision, reason);

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection updatedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(updatedProjection);
        assertEquals(BillStatus.REJECTED, updatedProjection.getStatus());
        assertEquals(BillProjection.ApprovalDecision.REJECTED, updatedProjection.getApprovalDecision());
        assertEquals(approverId, updatedProjection.getApproverId());
        assertEquals(reason, updatedProjection.getApprovalReason());
        assertEquals(timestamp, updatedProjection.getApprovedAt());
        assertTrue(updatedProjection.isTerminal());
    }

    @Test
    @Transactional
    @DisplayName("Should handle OcrRequestedEvent and update timestamp")
    void shouldHandleOcrRequestedEventAndUpdateTimestamp() {
        // Given - create bill with file attached
        String billId = "bill-ocr-request-123";
        Instant originalTime = Instant.now().minusSeconds(60);
        createBillProjection(billId, "Gas Bill", new BigDecimal("120.00"), BillStatus.FILE_ATTACHED, originalTime);

        Instant timestamp = Instant.now();
        OcrRequestedEvent event = new OcrRequestedEvent(billId);

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection updatedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(updatedProjection);
        assertEquals(BillStatus.FILE_ATTACHED, updatedProjection.getStatus()); // Status unchanged
        assertEquals(timestamp, updatedProjection.getUpdatedAt());
        assertTrue(updatedProjection.getUpdatedAt().isAfter(originalTime));
    }

    @Test
    @Transactional
    @DisplayName("Should handle events for non-existent bills gracefully")
    void shouldHandleEventsForNonExistentBillsGracefully() {
        // Given - no bill in database
        String nonExistentBillId = "non-existent-bill";

        Instant timestamp = Instant.now();
        FileAttachedEvent event = FileAttachedEvent.builder()
                .billId(nonExistentBillId)
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/test.pdf")
                .checksum("abc123")
                .build();

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> eventHandler.handle(event, timestamp));

        // Verify no projection was created
        assertFalse(billReadRepository.existsById(nonExistentBillId));
    }

    @Test
    @Transactional
    @DisplayName("Should serialize metadata correctly")
    void shouldSerializeMetadataCorrectly() {
        // Given
        String billId = "bill-metadata-123";
        Map<String, Object> metadata = Map.of(
                "source", "mobile",
                "category", "utilities",
                "tags", new String[]{"urgent", "recurring"},
                "amount", 150.75
        );
        Instant timestamp = Instant.now();

        BillCreatedEvent event = new BillCreatedEvent(billId, "Test Bill", new BigDecimal("150.75"), metadata);

        // When
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        BillProjection savedProjection = billReadRepository.findById(billId).orElse(null);
        assertNotNull(savedProjection);
        assertNotNull(savedProjection.getMetadataJson());

        // Verify the JSON can be parsed back
        assertDoesNotThrow(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedMetadata = objectMapper.readValue(
                    savedProjection.getMetadataJson(),
                    Map.class
            );
            assertEquals("mobile", parsedMetadata.get("source"));
            assertEquals("utilities", parsedMetadata.get("category"));
        });
    }

    /**
     * Helper method to create a BillProjection for testing.
     */
    private void createBillProjection(String billId, String title, BigDecimal total, BillStatus status) {
        createBillProjection(billId, title, total, status, Instant.now());
    }

    /**
     * Helper method to create a BillProjection for testing with specific timestamp.
     */
    private void createBillProjection(String billId, String title, BigDecimal total, BillStatus status, Instant timestamp) {
        BillProjection projection = BillProjection.builder()
                .id(billId)
                .title(title)
                .total(total)
                .status(status)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .version(1L)
                .build();

        entityManager.persist(projection);
        entityManager.flush();
    }
}
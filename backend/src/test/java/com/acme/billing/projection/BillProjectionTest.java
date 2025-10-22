package com.acme.billing.projection;

import com.acme.billing.domain.BillStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BillProjection entity.
 */
@DisplayName("BillProjection Tests")
class BillProjectionTest {

    @Test
    @DisplayName("Should create BillProjection with required fields")
    void shouldCreateBillProjectionWithRequiredFields() {
        // Given
        String id = "bill-123";
        String title = "Electric Bill";
        BigDecimal total = new BigDecimal("150.75");

        // When
        BillProjection projection = BillProjection.builder()
                .id(id)
                .title(title)
                .total(total)
                .status(BillStatus.CREATED)
                .build();

        // Then
        assertEquals(id, projection.getId());
        assertEquals(title, projection.getTitle());
        assertEquals(total, projection.getTotal());
        assertEquals(BillStatus.CREATED, projection.getStatus());
        assertNull(projection.getOcrExtractedText());
        assertNull(projection.getOcrExtractedTotal());
    }

    @Test
    @DisplayName("Should update status and version")
    void shouldUpdateStatusAndVersion() {
        // Given
        BillProjection projection = BillProjection.builder()
                .id("bill-123")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .status(BillStatus.CREATED)
                .version(1L)
                .build();

        // When
        projection.updateStatus(BillStatus.PROCESSED);

        // Then
        assertEquals(BillStatus.PROCESSED, projection.getStatus());
        assertEquals(BillProjection.ApprovalDecision.APPROVED, projection.getApprovalDecision());
    }

    @Test
    @DisplayName("Should update status to APPROVED and set approval decision")
    void shouldUpdateStatusToApprovedAndSetApprovalDecision() {
        // Given
        BillProjection projection = BillProjection.builder()
                .id("bill-123")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .status(BillStatus.PROCESSED)
                .version(1L)
                .build();

        // When
        projection.updateStatus(BillStatus.APPROVED);

        // Then
        assertEquals(BillStatus.APPROVED, projection.getStatus());
        assertEquals(BillProjection.ApprovalDecision.APPROVED, projection.getApprovalDecision());
    }

    @Test
    @DisplayName("Should update status to REJECTED and set approval decision")
    void shouldUpdateStatusToRejectedAndSetApprovalDecision() {
        // Given
        BillProjection projection = BillProjection.builder()
                .id("bill-123")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .status(BillStatus.PROCESSED)
                .version(1L)
                .build();

        // When
        projection.updateStatus(BillStatus.REJECTED);

        // Then
        assertEquals(BillStatus.REJECTED, projection.getStatus());
        assertEquals(BillProjection.ApprovalDecision.REJECTED, projection.getApprovalDecision());
    }

    @Test
    @DisplayName("Should identify terminal status correctly")
    void shouldIdentifyTerminalStatusCorrectly() {
        // Given
        BillProjection approvedBill = BillProjection.builder()
                .id("bill-1")
                .status(BillStatus.APPROVED)
                .build();

        BillProjection rejectedBill = BillProjection.builder()
                .id("bill-2")
                .status(BillStatus.REJECTED)
                .build();

        BillProjection processedBill = BillProjection.builder()
                .id("bill-3")
                .status(BillStatus.PROCESSED)
                .build();

        // Then
        assertTrue(approvedBill.isTerminal());
        assertTrue(rejectedBill.isTerminal());
        assertFalse(processedBill.isTerminal());
        assertFalse(BillProjection.builder().status(null).build().isTerminal());
    }

    @Test
    @DisplayName("Should identify OCR results correctly")
    void shouldIdentifyOcrResultsCorrectly() {
        // Given
        BillProjection withText = BillProjection.builder()
                .id("bill-1")
                .ocrExtractedText("Extracted text")
                .build();

        BillProjection withTotal = BillProjection.builder()
                .id("bill-2")
                .ocrExtractedTotal(new BigDecimal("100.00"))
                .build();

        BillProjection withTitle = BillProjection.builder()
                .id("bill-3")
                .ocrExtractedTitle("Extracted Title")
                .build();

        BillProjection withoutOcr = BillProjection.builder()
                .id("bill-4")
                .build();

        // Then
        assertTrue(withText.hasOcrResults());
        assertTrue(withTotal.hasOcrResults());
        assertTrue(withTitle.hasOcrResults());
        assertFalse(withoutOcr.hasOcrResults());
    }

    @Test
    @DisplayName("Should return effective total prioritizing OCR result")
    void shouldReturnEffectiveTotalPrioritizingOcrResult() {
        // Given
        BillProjection withOcr = BillProjection.builder()
                .id("bill-1")
                .total(new BigDecimal("100.00"))
                .ocrExtractedTotal(new BigDecimal("95.50"))
                .build();

        BillProjection withoutOcr = BillProjection.builder()
                .id("bill-2")
                .total(new BigDecimal("100.00"))
                .build();

        BillProjection withNullOcr = BillProjection.builder()
                .id("bill-3")
                .total(new BigDecimal("100.00"))
                .ocrExtractedTotal(null)
                .build();

        // Then
        assertEquals(new BigDecimal("95.50"), withOcr.getEffectiveTotal());
        assertEquals(new BigDecimal("100.00"), withoutOcr.getEffectiveTotal());
        assertEquals(new BigDecimal("100.00"), withNullOcr.getEffectiveTotal());
    }

    @Test
    @DisplayName("Should return effective title prioritizing OCR result")
    void shouldReturnEffectiveTitlePrioritizingOcrResult() {
        // Given
        BillProjection withOcr = BillProjection.builder()
                .id("bill-1")
                .title("Original Title")
                .ocrExtractedTitle("Extracted Title")
                .build();

        BillProjection withBlankOcr = BillProjection.builder()
                .id("bill-2")
                .title("Original Title")
                .ocrExtractedTitle("   ")
                .build();

        BillProjection withoutOcr = BillProjection.builder()
                .id("bill-3")
                .title("Original Title")
                .build();

        BillProjection withNullOcr = BillProjection.builder()
                .id("bill-4")
                .title("Original Title")
                .ocrExtractedTitle(null)
                .build();

        // Then
        assertEquals("Extracted Title", withOcr.getEffectiveTitle());
        assertEquals("Original Title", withBlankOcr.getEffectiveTitle());
        assertEquals("Original Title", withoutOcr.getEffectiveTitle());
        assertEquals("Original Title", withNullOcr.getEffectiveTitle());
    }

    @Test
    @DisplayName("Should pre-persist lifecycle methods")
    void shouldPrePersistLifecycleMethods() {
        // Given
        Instant before = Instant.now();
        BillProjection projection = BillProjection.builder()
                .id("bill-123")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .status(BillStatus.CREATED)
                .build();

        // Simulate @PrePersist
        projection.onCreate();

        // Then
        assertNotNull(projection.getCreatedAt());
        assertNotNull(projection.getUpdatedAt());
        assertEquals(projection.getCreatedAt(), projection.getUpdatedAt());
        assertEquals(0L, projection.getVersion());
        assertTrue(projection.getCreatedAt().isAfter(before) || projection.getCreatedAt().equals(before));
    }

    @Test
    @DisplayName("Should pre-update lifecycle methods")
    void shouldPreUpdateLifecycleMethods() {
        // Given
        BillProjection projection = BillProjection.builder()
                .id("bill-123")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .status(BillStatus.CREATED)
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now().minusSeconds(60))
                .version(2L)
                .build();

        Instant beforeUpdate = Instant.now();

        // Simulate @PreUpdate
        projection.onUpdate();

        // Then
        assertTrue(projection.getUpdatedAt().isAfter(beforeUpdate) || projection.getUpdatedAt().equals(beforeUpdate));
        assertEquals(3L, projection.getVersion());
        // createdAt should remain unchanged
        assertTrue(projection.getCreatedAt().isBefore(beforeUpdate));
    }
}
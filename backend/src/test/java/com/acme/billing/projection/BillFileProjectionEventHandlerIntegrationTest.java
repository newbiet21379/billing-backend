package com.acme.billing.projection;

import com.acme.billing.domain.events.FileAttachedEvent;
import com.acme.billing.projection.handler.BillFileProjectionEventHandler;
import com.acme.billing.projection.repository.BillFileReadRepository;
import com.acme.billing.projection.repository.BillReadRepository;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BillFileProjectionEventHandler.
 * Tests the complete event-to-projection flow with real database.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",
        "logging.level.org.hibernate.SQL=DEBUG"
})
@DisplayName("BillFileProjectionEventHandler Integration Tests")
class BillFileProjectionEventHandlerIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BillFileReadRepository billFileReadRepository;

    @Autowired
    private BillReadRepository billReadRepository;

    private BillFileProjectionEventHandler eventHandler;

    @BeforeEach
    void setUp() {
        eventHandler = new BillFileProjectionEventHandler(billFileReadRepository, billReadRepository);
    }

    @Test
    @Transactional
    @DisplayName("Should handle FileAttachedEvent and create file projection")
    void shouldHandleFileAttachedEventAndCreateFileProjection() {
        // Given - create parent bill first
        String billId = "bill-123";
        createBillProjection(billId, "Test Bill", new BigDecimal("100.00"));

        String filename = "invoice.pdf";
        String contentType = "application/pdf";
        Long fileSize = 2048L;
        String storagePath = "/bills/123/invoice.pdf";
        String checksum = "abc123def456";
        Instant attachedAt = Instant.now();

        FileAttachedEvent event = FileAttachedEvent.builder()
                .billId(billId)
                .filename(filename)
                .contentType(contentType)
                .fileSize(fileSize)
                .storagePath(storagePath)
                .checksum(checksum)
                .attachedAt(attachedAt)
                .build();

        // When
        Instant timestamp = Instant.now();
        eventHandler.handle(event, timestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> fileProjections = billFileReadRepository.findByBillId(billId);
        assertEquals(1, fileProjections.size());

        BillFileProjection savedFile = fileProjections.get(0);
        assertNotNull(savedFile.getId());
        assertEquals(billId, savedFile.getBillId());
        assertEquals(filename, savedFile.getFilename());
        assertEquals(contentType, savedFile.getContentType());
        assertEquals(fileSize, savedFile.getFileSize());
        assertEquals(storagePath, savedFile.getStoragePath());
        assertEquals(checksum, savedFile.getChecksum());
        assertEquals(attachedAt, savedFile.getAttachedAt());
        assertEquals(timestamp, savedFile.getCreatedAt());
        assertEquals(timestamp, savedFile.getUpdatedAt());
        assertEquals(0L, savedFile.getVersion());
    }

    @Test
    @Transactional
    @DisplayName("Should handle multiple file attachments for same bill")
    void shouldHandleMultipleFileAttachmentsForSameBill() {
        // Given - create parent bill
        String billId = "bill-multi-files";
        createBillProjection(billId, "Multi-file Bill", new BigDecimal("500.00"));

        // When - attach first file
        FileAttachedEvent event1 = FileAttachedEvent.builder()
                .billId(billId)
                .filename("invoice.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .storagePath("/bills/multi/invoice.pdf")
                .checksum("abc123")
                .build();

        eventHandler.handle(event1, Instant.now());

        // When - attach second file
        FileAttachedEvent event2 = FileAttachedEvent.builder()
                .billId(billId)
                .filename("receipt.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .storagePath("/bills/multi/receipt.jpg")
                .checksum("def456")
                .build();

        eventHandler.handle(event2, Instant.now());

        // Then
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> fileProjections = billFileReadRepository.findByBillId(billId);
        assertEquals(2, fileProjections.size());

        // Verify both files are present
        assertTrue(fileProjections.stream().anyMatch(f -> "invoice.pdf".equals(f.getFilename())));
        assertTrue(fileProjections.stream().anyMatch(f -> "receipt.jpg".equals(f.getFilename())));
    }

    @Test
    @Transactional
    @DisplayName("Should reject file attachment for non-existent bill")
    void shouldRejectFileAttachmentForNonExistentBill() {
        // Given - no bill in database
        String nonExistentBillId = "non-existent-bill";

        FileAttachedEvent event = FileAttachedEvent.builder()
                .billId(nonExistentBillId)
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/test.pdf")
                .checksum("abc123")
                .build();

        // When
        eventHandler.handle(event, Instant.now());

        // Then
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> fileProjections = billFileReadRepository.findByBillId(nonExistentBillId);
        assertEquals(0, fileProjections.size());
    }

    @Test
    @Transactional
    @DisplayName("Should handle batch file attachments")
    void shouldHandleBatchFileAttachments() {
        // Given - create parent bill
        String billId = "bill-batch";
        createBillProjection(billId, "Batch Bill", new BigDecimal("1000.00"));

        List<FileAttachedEvent> events = List.of(
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("file1.pdf")
                        .contentType("application/pdf")
                        .fileSize(1024L)
                        .storagePath("/batch/file1.pdf")
                        .checksum("hash1")
                        .build(),
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("file2.jpg")
                        .contentType("image/jpeg")
                        .fileSize(2048L)
                        .storagePath("/batch/file2.jpg")
                        .checksum("hash2")
                        .build(),
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("file3.doc")
                        .contentType("application/msword")
                        .fileSize(512L)
                        .storagePath("/batch/file3.doc")
                        .checksum("hash3")
                        .build()
        );

        // When
        eventHandler.handleBatchFileAttached(events, Instant.now());

        // Then
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> fileProjections = billFileReadRepository.findByBillId(billId);
        assertEquals(3, fileProjections.size());

        assertEquals(3, billFileReadRepository.countByBillId(billId));
    }

    @Test
    @Transactional
    @DisplayName("Should handle batch with some invalid attachments")
    void shouldHandleBatchWithSomeInvalidAttachments() {
        // Given - create parent bill
        String billId = "bill-batch-mixed";
        createBillProjection(billId, "Mixed Batch Bill", new BigDecimal("500.00"));

        List<FileAttachedEvent> events = List.of(
                // Valid event
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("valid.pdf")
                        .contentType("application/pdf")
                        .fileSize(1024L)
                        .storagePath("/valid.pdf")
                        .checksum("hash1")
                        .build(),
                // Invalid event - non-existent bill
                FileAttachedEvent.builder()
                        .billId("non-existent-bill")
                        .filename("invalid.pdf")
                        .contentType("application/pdf")
                        .fileSize(2048L)
                        .storagePath("/invalid.pdf")
                        .checksum("hash2")
                        .build(),
                // Another valid event
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("another-valid.jpg")
                        .contentType("image/jpeg")
                        .fileSize(512L)
                        .storagePath("/another-valid.jpg")
                        .checksum("hash3")
                        .build()
        );

        // When
        eventHandler.handleBatchFileAttached(events, Instant.now());

        // Then
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> validFiles = billFileReadRepository.findByBillId(billId);
        assertEquals(2, validFiles.size()); // Only 2 out of 3 should succeed

        List<BillFileProjection> invalidFiles = billFileReadRepository.findByBillId("non-existent-bill");
        assertEquals(0, invalidFiles.size());
    }

    @Test
    @Transactional
    @DisplayName("Should handle file removal")
    void shouldHandleFileRemoval() {
        // Given - create bill and file
        String billId = "bill-remove-file";
        createBillProjection(billId, "Remove File Bill", new BigDecimal("200.00"));

        FileAttachedEvent attachEvent = FileAttachedEvent.builder()
                .billId(billId)
                .filename("to-be-removed.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/to-be-removed.pdf")
                .checksum("removal-hash")
                .build();

        eventHandler.handle(attachEvent, Instant.now());
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> initialFiles = billFileReadRepository.findByBillId(billId);
        assertEquals(1, initialFiles.size());
        String fileId = initialFiles.get(0).getId();

        // When - remove the file
        Instant removalTimestamp = Instant.now();
        eventHandler.handleFileRemoved(billId, fileId, removalTimestamp);

        // Then
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> remainingFiles = billFileReadRepository.findByBillId(billId);
        assertEquals(0, remainingFiles.size());

        assertFalse(billFileReadRepository.existsById(fileId));
    }

    @Test
    @Transactional
    @DisplayName("Should reject file removal for wrong bill")
    void shouldRejectFileRemovalForWrongBill() {
        // Given - create two bills and one file
        String billId1 = "bill1";
        String billId2 = "bill2";
        createBillProjection(billId1, "Bill 1", new BigDecimal("100.00"));
        createBillProjection(billId2, "Bill 2", new BigDecimal("200.00"));

        FileAttachedEvent attachEvent = FileAttachedEvent.builder()
                .billId(billId1)
                .filename("file.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/file.pdf")
                .checksum("hash")
                .build();

        eventHandler.handle(attachEvent, Instant.now());
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> initialFiles = billFileReadRepository.findByBillId(billId1);
        assertEquals(1, initialFiles.size());
        String fileId = initialFiles.get(0).getId();

        // When - try to remove file from wrong bill
        eventHandler.handleFileRemoved(billId2, fileId, Instant.now());

        // Then - file should still exist
        entityManager.flush();
        entityManager.clear();

        List<BillFileProjection> remainingFiles = billFileReadRepository.findByBillId(billId1);
        assertEquals(1, remainingFiles.size()); // File should still exist
        assertTrue(billFileReadRepository.existsById(fileId));
    }

    @Test
    @Transactional
    @DisplayName("Should calculate storage metrics correctly")
    void shouldCalculateStorageMetricsCorrectly() {
        // Given - create bill and multiple files
        String billId = "bill-storage-metrics";
        createBillProjection(billId, "Storage Metrics Bill", new BigDecimal("300.00"));

        List<FileAttachedEvent> events = List.of(
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("small.pdf")
                        .contentType("application/pdf")
                        .fileSize(1024L) // 1 KB
                        .storagePath("/small.pdf")
                        .checksum("hash1")
                        .build(),
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("medium.pdf")
                        .contentType("application/pdf")
                        .fileSize(1048576L) // 1 MB
                        .storagePath("/medium.pdf")
                        .checksum("hash2")
                        .build(),
                FileAttachedEvent.builder()
                        .billId(billId)
                        .filename("large.pdf")
                        .contentType("application/pdf")
                        .fileSize(5242880L) // 5 MB
                        .storagePath("/large.pdf")
                        .checksum("hash3")
                        .build()
        );

        // When
        eventHandler.handleBatchFileAttached(events, Instant.now());

        // Then
        entityManager.flush();
        entityManager.clear();

        Long totalStorageUsed = billFileReadRepository.calculateTotalStorageUsed();
        Long expectedTotal = 1024L + 1048576L + 5242880L; // ~6.3 MB
        assertEquals(expectedTotal, totalStorageUsed);

        // Verify per-content-type storage
        Long pdfStorageUsed = billFileReadRepository.calculateStorageUsedByContentType("application/pdf");
        assertEquals(expectedTotal, pdfStorageUsed);

        // Verify large files detection
        List<BillFileProjection> largeFiles = billFileReadRepository.findLargeFiles();
        assertEquals(1, largeFiles.size()); // Only the 5MB file
        assertEquals("large.pdf", largeFiles.get(0).getFilename());
    }

    /**
     * Helper method to create a BillProjection for testing.
     */
    private void createBillProjection(String billId, String title, BigDecimal total) {
        BillProjection projection = BillProjection.builder()
                .id(billId)
                .title(title)
                .total(total)
                .status(com.acme.billing.domain.BillStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        entityManager.persist(projection);
        entityManager.flush();
    }
}
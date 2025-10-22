package com.acme.billing.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BillFileProjection entity.
 */
@DisplayName("BillFileProjection Tests")
class BillFileProjectionTest {

    @Test
    @DisplayName("Should create BillFileProjection with required fields")
    void shouldCreateBillFileProjectionWithRequiredFields() {
        // Given
        String id = "file-123";
        String billId = "bill-123";
        String filename = "invoice.pdf";
        String contentType = "application/pdf";
        Long fileSize = 1024L;
        String storagePath = "/bills/123/invoice.pdf";
        String checksum = "abc123";

        // When
        BillFileProjection projection = BillFileProjection.builder()
                .id(id)
                .billId(billId)
                .filename(filename)
                .contentType(contentType)
                .fileSize(fileSize)
                .storagePath(storagePath)
                .checksum(checksum)
                .build();

        // Then
        assertEquals(id, projection.getId());
        assertEquals(billId, projection.getBillId());
        assertEquals(filename, projection.getFilename());
        assertEquals(contentType, projection.getContentType());
        assertEquals(fileSize, projection.getFileSize());
        assertEquals(storagePath, projection.getStoragePath());
        assertEquals(checksum, projection.getChecksum());
        assertNotNull(projection.getAttachedAt());
    }

    @Test
    @DisplayName("Should identify image files correctly")
    void shouldIdentifyImageFilesCorrectly() {
        // Given
        BillFileProjection imageFile = BillFileProjection.builder()
                .id("file-1")
                .billId("bill-1")
                .filename("receipt.jpg")
                .contentType("image/jpeg")
                .build();

        BillFileProjection pdfFile = BillFileProjection.builder()
                .id("file-2")
                .billId("bill-1")
                .filename("invoice.pdf")
                .contentType("application/pdf")
                .build();

        BillFileProjection unknownFile = BillFileProjection.builder()
                .id("file-3")
                .billId("bill-1")
                .filename("document.doc")
                .contentType(null)
                .build();

        // Then
        assertTrue(imageFile.isImage());
        assertFalse(pdfFile.isImage());
        assertFalse(unknownFile.isImage());
    }

    @Test
    @DisplayName("Should identify PDF files correctly")
    void shouldIdentifyPdfFilesCorrectly() {
        // Given
        BillFileProjection pdfFile = BillFileProjection.builder()
                .id("file-1")
                .billId("bill-1")
                .filename("invoice.pdf")
                .contentType("application/pdf")
                .build();

        BillFileProjection imageFile = BillFileProjection.builder()
                .id("file-2")
                .billId("bill-1")
                .filename("receipt.jpg")
                .contentType("image/jpeg")
                .build();

        // Then
        assertTrue(pdfFile.isPdf());
        assertFalse(imageFile.isPdf());
    }

    @Test
    @DisplayName("Should extract file extension correctly")
    void shouldExtractFileExtensionCorrectly() {
        // Given
        BillFileProjection pdfFile = BillFileProjection.builder()
                .id("file-1")
                .filename("document.pdf")
                .build();

        BillFileProjection jpegFile = BillFileProjection.builder()
                .id("file-2")
                .filename("image.JPG")
                .build();

        BillFileProjection noExtension = BillFileProjection.builder()
                .id("file-3")
                .filename("document")
                .build();

        BillFileProjection multipleDots = BillFileProjection.builder()
                .id("file-4")
                .filename("backup.document.pdf")
                .build();

        BillFileProjection nullFilename = BillFileProjection.builder()
                .id("file-5")
                .filename(null)
                .build();

        // Then
        assertEquals("pdf", pdfFile.getFileExtension());
        assertEquals("jpg", jpegFile.getFileExtension());
        assertEquals("", noExtension.getFileExtension());
        assertEquals("pdf", multipleDots.getFileExtension());
        assertEquals("", nullFilename.getFileExtension());
    }

    @Test
    @DisplayName("Should format file size correctly")
    void shouldFormatFileSizeCorrectly() {
        // Given
        BillFileProjection bytesFile = BillFileProjection.builder()
                .id("file-1")
                .fileSize(512L)
                .build();

        BillFileProjection kbFile = BillFileProjection.builder()
                .id("file-2")
                .fileSize(1536L) // 1.5 KB
                .build();

        BillFileProjection mbFile = BillFileProjection.builder()
                .id("file-3")
                .fileSize(2621440L) // 2.5 MB
                .build();

        BillFileProjection gbFile = BillFileProjection.builder()
                .id("file-4")
                .fileSize(3221225472L) // 3 GB
                .build();

        BillFileProjection nullSizeFile = BillFileProjection.builder()
                .id("file-5")
                .fileSize(null)
                .build();

        // Then
        assertEquals("512 B", bytesFile.getFormattedFileSize());
        assertEquals("1.5 KB", kbFile.getFormattedFileSize());
        assertEquals("2.5 MB", mbFile.getFormattedFileSize());
        assertEquals("3.0 GB", gbFile.getFormattedFileSize());
        assertEquals("Unknown", nullSizeFile.getFormattedFileSize());
    }

    @Test
    @DisplayName("Should handle file size formatting edge cases")
    void shouldHandleFileSizeFormattingEdgeCases() {
        // Given
        BillFileProjection zeroBytes = BillFileProjection.builder()
                .id("file-1")
                .fileSize(0L)
                .build();

        BillFileProjection exactlyOneKB = BillFileProjection.builder()
                .id("file-2")
                .fileSize(1024L)
                .build();

        BillFileProjection exactlyOneMB = BillFileProjection.builder()
                .id("file-3")
                .fileSize(1048576L) // 1024 * 1024
                .build();

        BillFileProjection exactlyOneGB = BillFileProjection.builder()
                .id("file-4")
                .fileSize(1073741824L) // 1024 * 1024 * 1024
                .build();

        // Then
        assertEquals("0 B", zeroBytes.getFormattedFileSize());
        assertEquals("1.0 KB", exactlyOneKB.getFormattedFileSize());
        assertEquals("1.0 MB", exactlyOneMB.getFormattedFileSize());
        assertEquals("1.0 GB", exactlyOneGB.getFormattedFileSize());
    }

    @Test
    @DisplayName("Should pre-persist lifecycle methods")
    void shouldPrePersistLifecycleMethods() {
        // Given
        Instant before = Instant.now();
        BillFileProjection projection = BillFileProjection.builder()
                .id("file-123")
                .billId("bill-123")
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/test.pdf")
                .checksum("abc123")
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
        BillFileProjection projection = BillFileProjection.builder()
                .id("file-123")
                .billId("bill-123")
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/test.pdf")
                .checksum("abc123")
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

    @Test
    @DisplayName("Should handle null attachedAt gracefully")
    void shouldHandleNullAttachedAtGracefully() {
        // Given
        Instant timestamp = Instant.now();
        BillFileProjection projection = BillFileProjection.builder()
                .id("file-123")
                .billId("bill-123")
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .storagePath("/test.pdf")
                .checksum("abc123")
                .attachedAt(null)
                .build();

        // Simulate @PrePersist with null attachedAt
        projection.onCreate();

        // Then
        assertNotNull(projection.getAttachedAt());
        // attachedAt should be set to same as createdAt when null
        assertEquals(projection.getCreatedAt(), projection.getAttachedAt());
    }
}
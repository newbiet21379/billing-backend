package com.acme.billing.projection.handler;

import com.acme.billing.domain.events.FileAttachedEvent;
import com.acme.billing.projection.BillFileProjection;
import com.acme.billing.projection.repository.BillFileReadRepository;
import com.acme.billing.projection.repository.BillReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Event handler responsible for updating BillFileProjection entities in response to domain events.
 * This component manages file attachment projections for efficient querying.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillFileProjectionEventHandler {

    private final BillFileReadRepository billFileReadRepository;
    private final BillReadRepository billReadRepository;

    /**
     * Handles FileAttachedEvent by creating a new BillFileProjection.
     * Also updates the parent bill's status if needed.
     */
    @EventHandler
    @Transactional
    public void handle(FileAttachedEvent event, @Timestamp Instant timestamp) {
        log.debug("Handling FileAttachedEvent for billId: {}, filename: {}", event.getBillId(), event.getFilename());

        // Verify that the bill exists before creating file projection
        if (!billReadRepository.existsById(event.getBillId())) {
            log.error("Cannot attach file to non-existent bill: {}", event.getBillId());
            return;
        }

        BillFileProjection fileProjection = BillFileProjection.builder()
                .id(generateFileId())
                .billId(event.getBillId())
                .filename(event.getFilename())
                .contentType(event.getContentType())
                .fileSize(event.getFileSize())
                .storagePath(event.getStoragePath())
                .checksum(event.getChecksum())
                .attachedAt(event.getAttachedAt() != null ? event.getAttachedAt() : timestamp)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .version(0L)
                .build();

        billFileReadRepository.save(fileProjection);
        log.info("Created BillFileProjection for billId: {}, fileId: {}, filename: {}",
                event.getBillId(), fileProjection.getId(), event.getFilename());

        // Log file attachment metrics
        logFileAttachmentMetrics(event.getBillId(), event.getFileSize());
    }

    /**
     * Handles case where a file attachment might be removed (not currently implemented in domain events).
     * This method is prepared for future extensibility.
     */
    @Transactional
    public void handleFileRemoved(String billId, String fileId, @Timestamp Instant timestamp) {
        log.debug("Handling file removal for billId: {}, fileId: {}", billId, fileId);

        billFileReadRepository.findById(fileId)
                .ifPresentOrElse(fileProjection -> {
                    // Verify the file belongs to the specified bill
                    if (!billId.equals(fileProjection.getBillId())) {
                        log.error("File {} does not belong to bill {}", fileId, billId);
                        return;
                    }

                    billFileReadRepository.delete(fileProjection);
                    log.info("Deleted BillFileProjection for billId: {}, fileId: {}", billId, fileId);
                }, () -> {
                    log.warn("BillFileProjection not found for fileId: {}", fileId);
                });
    }

    /**
     * Handles case where multiple files might be attached to a bill in batch.
     * This method is prepared for future extensibility.
     */
    @Transactional
    public void handleBatchFileAttached(java.util.List<FileAttachedEvent> events, @Timestamp Instant timestamp) {
        log.debug("Handling batch file attachment for {} files", events.size());

        int successCount = 0;
        int failureCount = 0;

        for (FileAttachedEvent event : events) {
            try {
                // Verify that the bill exists
                if (!billReadRepository.existsById(event.getBillId())) {
                    log.error("Cannot attach file to non-existent bill: {}", event.getBillId());
                    failureCount++;
                    continue;
                }

                BillFileProjection fileProjection = BillFileProjection.builder()
                        .id(generateFileId())
                        .billId(event.getBillId())
                        .filename(event.getFilename())
                        .contentType(event.getContentType())
                        .fileSize(event.getFileSize())
                        .storagePath(event.getStoragePath())
                        .checksum(event.getChecksum())
                        .attachedAt(event.getAttachedAt() != null ? event.getAttachedAt() : timestamp)
                        .createdAt(timestamp)
                        .updatedAt(timestamp)
                        .version(0L)
                        .build();

                billFileReadRepository.save(fileProjection);
                successCount++;
                log.debug("Created BillFileProjection for billId: {}, fileId: {}", event.getBillId(), fileProjection.getId());

            } catch (Exception e) {
                log.error("Failed to create file projection for billId: {}, filename: {}",
                        event.getBillId(), event.getFilename(), e);
                failureCount++;
            }
        }

        log.info("Batch file attachment completed: {} successful, {} failed", successCount, failureCount);
    }

    /**
     * Generates a unique ID for file projections.
     */
    private String generateFileId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Logs metrics about file attachments for monitoring purposes.
     */
    private void logFileAttachmentMetrics(String billId, Long fileSize) {
        long fileCount = billFileReadRepository.countByBillId(billId);
        Long totalStorageUsed = billFileReadRepository.calculateTotalStorageUsed();

        log.info("File attachment metrics - BillId: {}, FileCount: {}, CurrentFileSize: {}, TotalStorageUsed: {} bytes",
                billId, fileCount, fileSize, totalStorageUsed != null ? totalStorageUsed : 0L);

        // Warn about unusually large files
        if (fileSize != null && fileSize > 10 * 1024 * 1024) { // 10MB
            log.warn("Large file attached: {} bytes for billId: {}", fileSize, billId);
        }
    }

    /**
     * Validates file attachment data before creating projection.
     */
    private boolean validateFileAttachment(FileAttachedEvent event) {
        if (event.getBillId() == null || event.getBillId().isBlank()) {
            log.error("Bill ID is required for file attachment");
            return false;
        }

        if (event.getFilename() == null || event.getFilename().isBlank()) {
            log.error("Filename is required for file attachment");
            return false;
        }

        if (event.getContentType() == null || event.getContentType().isBlank()) {
            log.error("Content type is required for file attachment");
            return false;
        }

        if (event.getFileSize() != null && event.getFileSize() < 0) {
            log.error("File size cannot be negative: {}", event.getFileSize());
            return false;
        }

        if (event.getStoragePath() == null || event.getStoragePath().isBlank()) {
            log.error("Storage path is required for file attachment");
            return false;
        }

        if (event.getChecksum() == null || event.getChecksum().isBlank()) {
            log.error("Checksum is required for file attachment");
            return false;
        }

        return true;
    }
}
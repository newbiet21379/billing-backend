package com.acme.billing.projection.handler;

import com.acme.billing.domain.BillStatus;
import com.acme.billing.domain.events.*;
import com.acme.billing.projection.BillProjection;
import com.acme.billing.projection.repository.BillReadRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Event handler responsible for updating BillProjection entities in response to domain events.
 * This component bridges the gap between the write model (domain events) and read model (projections).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillProjectionEventHandler {

    private final BillReadRepository billReadRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handles BillCreatedEvent by creating a new BillProjection.
     */
    @EventHandler
    @Transactional
    public void handle(BillCreatedEvent event, @Timestamp Instant timestamp) {
        log.debug("Handling BillCreatedEvent for billId: {}", event.getBillId());

        BillProjection projection = BillProjection.builder()
                .id(event.getBillId())
                .title(event.getTitle())
                .total(event.getTotal())
                .status(BillStatus.CREATED)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .metadataJson(serializeMetadata(event.getMetadata()))
                .version(0L)
                .build();

        billReadRepository.save(projection);
        log.info("Created BillProjection for billId: {}", event.getBillId());
    }

    /**
     * Handles FileAttachedEvent by updating the bill status to FILE_ATTACHED.
     */
    @EventHandler
    @Transactional
    public void handle(FileAttachedEvent event, @Timestamp Instant timestamp) {
        log.debug("Handling FileAttachedEvent for billId: {}", event.getBillId());

        billReadRepository.findById(event.getBillId())
                .ifPresentOrElse(projection -> {
                    projection.updateStatus(BillStatus.FILE_ATTACHED);
                    projection.setUpdatedAt(timestamp);
                    billReadRepository.save(projection);
                    log.info("Updated BillProjection status to FILE_ATTACHED for billId: {}", event.getBillId());
                }, () -> {
                    log.warn("BillProjection not found for FileAttachedEvent billId: {}", event.getBillId());
                });
    }

    /**
     * Handles OcrCompletedEvent by updating the bill with OCR results and status.
     */
    @EventHandler
    @Transactional
    public void handle(OcrCompletedEvent event, @Timestamp Instant timestamp) {
        log.debug("Handling OcrCompletedEvent for billId: {}", event.getBillId());

        billReadRepository.findById(event.getBillId())
                .ifPresentOrElse(projection -> {
                    projection.updateStatus(BillStatus.PROCESSED);
                    projection.setOcrExtractedText(event.getExtractedText());
                    projection.setOcrExtractedTotal(event.getExtractedTotal());
                    projection.setOcrExtractedTitle(event.getExtractedTitle());
                    projection.setOcrConfidence(event.getConfidence());
                    projection.setOcrProcessingTime(event.getProcessingTime());
                    projection.setUpdatedAt(timestamp);

                    billReadRepository.save(projection);
                    log.info("Updated BillProjection with OCR results for billId: {}", event.getBillId());
                }, () -> {
                    log.warn("BillProjection not found for OcrCompletedEvent billId: {}", event.getBillId());
                });
    }

    /**
     * Handles BillApprovedEvent by updating the bill with approval information.
     */
    @EventHandler
    @Transactional
    public void handle(BillApprovedEvent event, @Timestamp Instant timestamp) {
        log.debug("Handling BillApprovedEvent for billId: {}", event.getBillId());

        billReadRepository.findById(event.getBillId())
                .ifPresentOrElse(projection -> {
                    // Update status based on approval decision
                    BillStatus newStatus = event.getDecision() == com.acme.billing.api.commands.ApproveBillCommand.ApprovalDecision.APPROVED
                            ? BillStatus.APPROVED
                            : BillStatus.REJECTED;

                    projection.updateStatus(newStatus);
                    projection.setApproverId(event.getApproverId());
                    projection.setApprovalDecision(event.getDecision() == com.acme.billing.api.commands.ApproveBillCommand.ApprovalDecision.APPROVED
                            ? BillProjection.ApprovalDecision.APPROVED
                            : BillProjection.ApprovalDecision.REJECTED);
                    projection.setApprovalReason(event.getReason());
                    projection.setApprovedAt(timestamp);
                    projection.setUpdatedAt(timestamp);

                    billReadRepository.save(projection);
                    log.info("Updated BillProjection with approval decision: {} for billId: {}",
                            event.getDecision(), event.getBillId());
                }, () -> {
                    log.warn("BillProjection not found for BillApprovedEvent billId: {}", event.getBillId());
                });
    }

    /**
     * Handles OcrRequestedEvent by updating the bill status to indicate OCR processing has started.
     */
    @EventHandler
    @Transactional
    public void handle(OcrRequestedEvent event, @Timestamp Instant timestamp) {
        log.debug("Handling OcrRequestedEvent for billId: {}", event.getBillId());

        billReadRepository.findById(event.getBillId())
                .ifPresentOrElse(projection -> {
                    // Status remains FILE_ATTACHED, but we update the timestamp
                    projection.setUpdatedAt(timestamp);
                    billReadRepository.save(projection);
                    log.info("Updated BillProjection timestamp for OCR request for billId: {}", event.getBillId());
                }, () -> {
                    log.warn("BillProjection not found for OcrRequestedEvent billId: {}", event.getBillId());
                });
    }

    /**
     * Serializes metadata map to JSON string for storage.
     */
    private String serializeMetadata(java.util.Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata for bill projection", e);
            return null;
        }
    }

    /**
     * Generates a unique ID for bill file projections when needed.
     */
    private String generateFileId() {
        return UUID.randomUUID().toString();
    }
}
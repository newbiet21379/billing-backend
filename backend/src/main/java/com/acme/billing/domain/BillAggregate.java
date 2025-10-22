package com.acme.billing.domain;

import com.acme.billing.api.commands.*;
import com.acme.billing.domain.events.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Aggregate responsible for managing the lifecycle of a Bill.
 * Handles all bill-related commands and emits corresponding events.
 */
@Aggregate
public class BillAggregate {

    private static final Logger log = LoggerFactory.getLogger(BillAggregate.class);

    @AggregateIdentifier
    private String billId;

    private String title;
    private BigDecimal total;
    private Map<String, Object> metadata;
    private BillStatus status;
    private Instant createdAt;

    // File attachment information
    private String filename;
    private String contentType;
    private Long fileSize;
    private String storagePath;
    private String checksum;
    private Instant fileAttachedAt;

    // OCR processing information
    private String extractedText;
    private BigDecimal extractedTotal;
    private String extractedTitle;
    private String ocrConfidence;
    private String ocrProcessingTime;
    private Instant ocrCompletedAt;

    // Approval information
    private String approverId;
    private ApproveBillCommand.ApprovalDecision approvalDecision;
    private String approvalReason;
    private Instant approvedAt;

    // Required by Axon
    protected BillAggregate() {}

    /**
     * Command handler for creating a new bill.
     */
    @CommandHandler
    @CreationPolicy(CreationPolicy.ALWAYS)
    public BillAggregate(CreateBillCommand command) {
        log.debug("Creating new bill with ID: {}", command.getBillId());

        validateCreateBillCommand(command);

        BillCreatedEvent event = new BillCreatedEvent(
            command.getBillId(),
            command.getTitle(),
            command.getTotal(),
            command.getMetadata()
        );

        AggregateLifecycle.apply(event);
    }

    /**
     * Command handler for attaching a file to a bill.
     */
    @CommandHandler
    public void handle(AttachFileCommand command) {
        log.debug("Attaching file to bill: {}", command.getBillId());

        validateAttachFileCommand(command);

        // Apply file attachment event
        FileAttachedEvent fileEvent = new FileAttachedEvent(
            command.getBillId(),
            command.getFilename(),
            command.getContentType(),
            command.getFileSize(),
            command.getStoragePath(),
            command.getChecksum()
        );
        AggregateLifecycle.apply(fileEvent);

        // Automatically trigger OCR processing
        OcrRequestedEvent ocrEvent = new OcrRequestedEvent(
            command.getBillId(),
            command.getFilename(),
            command.getStoragePath()
        );
        AggregateLifecycle.apply(ocrEvent);
    }

    /**
     * Command handler for applying OCR results to a bill.
     */
    @CommandHandler
    public void handle(ApplyOcrResultCommand command) {
        log.debug("Applying OCR results to bill: {}", command.getBillId());

        validateApplyOcrResultCommand(command);

        OcrCompletedEvent event = new OcrCompletedEvent(
            command.getBillId(),
            command.getExtractedText(),
            command.getExtractedTotal(),
            command.getExtractedTitle(),
            command.getConfidence(),
            command.getProcessingTime()
        );

        AggregateLifecycle.apply(event);
    }

    /**
     * Command handler for approving or rejecting a bill.
     */
    @CommandHandler
    public void handle(ApproveBillCommand command) {
        log.debug("Processing approval decision for bill: {} with decision: {}",
                 command.getBillId(), command.getDecision());

        validateApproveBillCommand(command);

        BillApprovedEvent event = new BillApprovedEvent(
            command.getBillId(),
            command.getApproverId(),
            command.getDecision(),
            command.getReason()
        );

        AggregateLifecycle.apply(event);
    }

    // Event Sourcing Handlers

    @EventSourcingHandler
    public void on(BillCreatedEvent event) {
        log.debug("Applying BillCreatedEvent for bill: {}", event.getBillId());

        this.billId = event.getBillId();
        this.title = event.getTitle();
        this.total = event.getTotal();
        this.metadata = event.getMetadata();
        this.status = BillStatus.CREATED;
        this.createdAt = event.getCreatedAt();
    }

    @EventSourcingHandler
    public void on(FileAttachedEvent event) {
        log.debug("Applying FileAttachedEvent for bill: {}", event.getBillId());

        this.filename = event.getFilename();
        this.contentType = event.getContentType();
        this.fileSize = event.getFileSize();
        this.storagePath = event.getStoragePath();
        this.checksum = event.getChecksum();
        this.fileAttachedAt = event.getAttachedAt();
        this.status = BillStatus.FILE_ATTACHED;
    }

    @EventSourcingHandler
    public void on(OcrRequestedEvent event) {
        log.debug("Applying OcrRequestedEvent for bill: {}", event.getBillId());
        // Status remains FILE_ATTACHED, OCR processing is in progress
    }

    @EventSourcingHandler
    public void on(OcrCompletedEvent event) {
        log.debug("Applying OcrCompletedEvent for bill: {}", event.getBillId());

        this.extractedText = event.getExtractedText();
        this.extractedTotal = event.getExtractedTotal();
        this.extractedTitle = event.getExtractedTitle();
        this.ocrConfidence = event.getConfidence();
        this.ocrProcessingTime = event.getProcessingTime();
        this.ocrCompletedAt = event.getCompletedAt();
        this.status = BillStatus.PROCESSED;
    }

    @EventSourcingHandler
    public void on(BillApprovedEvent event) {
        log.debug("Applying BillApprovedEvent for bill: {} with decision: {}",
                  event.getBillId(), event.getDecision());

        this.approverId = event.getApproverId();
        this.approvalDecision = event.getDecision();
        this.approvalReason = event.getReason();
        this.approvedAt = event.getApprovedAt();

        // Update status based on decision
        this.status = event.getDecision() == ApproveBillCommand.ApprovalDecision.APPROVED
            ? BillStatus.APPROVED
            : BillStatus.REJECTED;
    }

    // Validation Methods

    private void validateCreateBillCommand(CreateBillCommand command) {
        if (command.getBillId() == null || command.getBillId().trim().isEmpty()) {
            throw new IllegalArgumentException("Bill ID is required");
        }
        if (command.getTitle() == null || command.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (command.getTotal() == null || command.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than 0");
        }
    }

    private void validateAttachFileCommand(AttachFileCommand command) {
        if (!command.getBillId().equals(this.billId)) {
            throw new IllegalArgumentException("Bill ID mismatch");
        }

        if (this.status != BillStatus.CREATED) {
            throw new IllegalStateException("Cannot attach file to bill in status: " + this.status);
        }

        if (command.getFilename() == null || command.getFilename().trim().isEmpty()) {
            throw new IllegalArgumentException("Filename is required");
        }
        if (command.getContentType() == null || command.getContentType().trim().isEmpty()) {
            throw new IllegalArgumentException("Content type is required");
        }
        if (command.getFileSize() == null || command.getFileSize() <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
    }

    private void validateApplyOcrResultCommand(ApplyOcrResultCommand command) {
        if (!command.getBillId().equals(this.billId)) {
            throw new IllegalArgumentException("Bill ID mismatch");
        }

        if (this.status != BillStatus.FILE_ATTACHED) {
            throw new IllegalStateException("Cannot apply OCR results to bill in status: " + this.status);
        }

        if (command.getExtractedText() == null || command.getExtractedText().trim().isEmpty()) {
            throw new IllegalArgumentException("Extracted text is required");
        }
    }

    private void validateApproveBillCommand(ApproveBillCommand command) {
        if (!command.getBillId().equals(this.billId)) {
            throw new IllegalArgumentException("Bill ID mismatch");
        }

        if (!this.status.canBeApproved()) {
            throw new IllegalStateException("Cannot approve bill in status: " + this.status);
        }

        if (command.getApproverId() == null || command.getApproverId().trim().isEmpty()) {
            throw new IllegalArgumentException("Approver ID is required");
        }

        if (command.getDecision() == null) {
            throw new IllegalArgumentException("Approval decision is required");
        }
    }
}
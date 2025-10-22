package com.acme.billing.testdata;

import com.acme.billing.api.commands.*;
import com.acme.billing.api.commands.ApproveBillCommand.ApprovalDecision;
import com.acme.billing.domain.events.*;
import com.acme.billing.domain.BillStatus;
import com.acme.billing.projection.BillProjection;
import com.acme.billing.projection.BillFileProjection;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Factory class for creating test data related to bills.
 * Provides builders and factory methods for commands, events, and entities.
 */
public class BillTestDataFactory {

    // Constants for testing
    public static final String DEFAULT_BILL_ID = "bill-test-123";
    public static final String DEFAULT_TITLE = "Test Bill";
    public static final BigDecimal DEFAULT_TOTAL = new BigDecimal("150.75");
    public static final String DEFAULT_APPROVER_ID = "approver-456";
    public static final String DEFAULT_FILENAME = "invoice.pdf";
    public static final String DEFAULT_CONTENT_TYPE = "application/pdf";
    public static final Long DEFAULT_FILE_SIZE = 1024L;
    public static final String DEFAULT_STORAGE_PATH = "/bills/invoice.pdf";
    public static final String DEFAULT_CHECKSUM = "abc123";
    public static final String DEFAULT_EXTRACTED_TEXT = "Extracted text content";
    public static final BigDecimal DEFAULT_EXTRACTED_TOTAL = new BigDecimal("160.00");
    public static final String DEFAULT_EXTRACTED_TITLE = "Extracted Title";
    public static final String DEFAULT_OCR_CONFIDENCE = "95%";
    public static final String DEFAULT_OCR_PROCESSING_TIME = "2.5s";

    /**
     * Creates a default CreateBillCommand for testing.
     */
    public static CreateBillCommand createBillCommand() {
        return CreateBillCommand.create(
            DEFAULT_BILL_ID,
            DEFAULT_TITLE,
            DEFAULT_TOTAL,
            Map.of("vendor", "Test Corp", "category", "utilities")
        );
    }

    /**
     * Creates a CreateBillCommand with custom values.
     */
    public static CreateBillCommand createBillCommand(String billId, String title, BigDecimal total, Map<String, String> metadata) {
        return CreateBillCommand.create(billId, title, total, metadata);
    }

    /**
     * Creates a CreateBillCommand with generated ID.
     */
    public static CreateBillCommand createBillCommandWithGeneratedId() {
        return CreateBillCommand.create(
            null,
            DEFAULT_TITLE,
            DEFAULT_TOTAL,
            Map.of("vendor", "Auto Test Corp")
        );
    }

    /**
     * Creates a default AttachFileCommand for testing.
     */
    public static AttachFileCommand attachFileCommand() {
        return AttachFileCommand.create(
            DEFAULT_BILL_ID,
            DEFAULT_FILENAME,
            DEFAULT_CONTENT_TYPE,
            DEFAULT_FILE_SIZE,
            DEFAULT_STORAGE_PATH,
            DEFAULT_CHECKSUM
        );
    }

    /**
     * Creates a default ApplyOcrResultCommand for testing.
     */
    public static ApplyOcrResultCommand applyOcrResultCommand() {
        return ApplyOcrResultCommand.create(
            DEFAULT_BILL_ID,
            DEFAULT_EXTRACTED_TEXT,
            DEFAULT_EXTRACTED_TOTAL,
            DEFAULT_EXTRACTED_TITLE,
            DEFAULT_OCR_CONFIDENCE,
            DEFAULT_OCR_PROCESSING_TIME
        );
    }

    /**
     * Creates an approved ApproveBillCommand for testing.
     */
    public static ApproveBillCommand approveBillCommand() {
        return ApproveBillCommand.create(
            DEFAULT_BILL_ID,
            DEFAULT_APPROVER_ID,
            ApprovalDecision.APPROVED,
            "All validation checks passed"
        );
    }

    /**
     * Creates a rejected ApproveBillCommand for testing.
     */
    public static ApproveBillCommand rejectBillCommand() {
        return ApproveBillCommand.create(
            DEFAULT_BILL_ID,
            DEFAULT_APPROVER_ID,
            ApprovalDecision.REJECTED,
            "Amount mismatch detected"
        );
    }

    /**
     * Creates a default BillCreatedEvent for testing.
     */
    public static BillCreatedEvent billCreatedEvent() {
        return new BillCreatedEvent(
            DEFAULT_BILL_ID,
            DEFAULT_TITLE,
            DEFAULT_TOTAL,
            Map.of("vendor", "Test Corp", "category", "utilities")
        );
    }

    /**
     * Creates a default FileAttachedEvent for testing.
     */
    public static FileAttachedEvent fileAttachedEvent() {
        return new FileAttachedEvent(
            DEFAULT_BILL_ID,
            DEFAULT_FILENAME,
            DEFAULT_CONTENT_TYPE,
            DEFAULT_FILE_SIZE,
            DEFAULT_STORAGE_PATH,
            DEFAULT_CHECKSUM
        );
    }

    /**
     * Creates a default OcrRequestedEvent for testing.
     */
    public static OcrRequestedEvent ocrRequestedEvent() {
        return new OcrRequestedEvent(DEFAULT_BILL_ID, DEFAULT_FILENAME, DEFAULT_STORAGE_PATH);
    }

    /**
     * Creates a default OcrCompletedEvent for testing.
     */
    public static OcrCompletedEvent ocrCompletedEvent() {
        return new OcrCompletedEvent(
            DEFAULT_BILL_ID,
            DEFAULT_EXTRACTED_TEXT,
            DEFAULT_EXTRACTED_TOTAL,
            DEFAULT_EXTRACTED_TITLE,
            DEFAULT_OCR_CONFIDENCE,
            DEFAULT_OCR_PROCESSING_TIME
        );
    }

    /**
     * Creates an approved BillApprovedEvent for testing.
     */
    public static BillApprovedEvent billApprovedEvent() {
        return new BillApprovedEvent(
            DEFAULT_BILL_ID,
            DEFAULT_APPROVER_ID,
            ApprovalDecision.APPROVED,
            "All validation checks passed"
        );
    }

    /**
     * Creates a rejected BillApprovedEvent for testing.
     */
    public static BillApprovedEvent billRejectedEvent() {
        return new BillApprovedEvent(
            DEFAULT_BILL_ID,
            DEFAULT_APPROVER_ID,
            ApprovalDecision.REJECTED,
            "Amount mismatch detected"
        );
    }

    /**
     * Creates a complete BillProjection for testing.
     */
    public static BillProjection billProjection() {
        return BillProjection.builder()
            .billId(DEFAULT_BILL_ID)
            .title(DEFAULT_TITLE)
            .total(DEFAULT_TOTAL)
            .metadata(Map.of("vendor", "Test Corp", "category", "utilities"))
            .status(BillStatus.CREATED)
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Creates a complete BillFileProjection for testing.
     */
    public static BillFileProjection billFileProjection() {
        return BillFileProjection.builder()
            .billId(DEFAULT_BILL_ID)
            .filename(DEFAULT_FILENAME)
            .contentType(DEFAULT_CONTENT_TYPE)
            .fileSize(DEFAULT_FILE_SIZE)
            .storagePath(DEFAULT_STORAGE_PATH)
            .checksum(DEFAULT_CHECKSUM)
            .uploadedAt(Instant.now())
            .build();
    }

    /**
     * Creates a complete BillProjection in APPROVED status for testing.
     */
    public static BillProjection approvedBillProjection() {
        return BillProjection.builder()
            .billId(DEFAULT_BILL_ID)
            .title(DEFAULT_TITLE)
            .total(DEFAULT_TOTAL)
            .metadata(Map.of("vendor", "Test Corp"))
            .status(BillStatus.APPROVED)
            .createdAt(Instant.now())
            .filename(DEFAULT_FILENAME)
            .contentType(DEFAULT_CONTENT_TYPE)
            .fileSize(DEFAULT_FILE_SIZE)
            .storagePath(DEFAULT_STORAGE_PATH)
            .checksum(DEFAULT_CHECKSUM)
            .fileAttachedAt(Instant.now())
            .extractedText(DEFAULT_EXTRACTED_TEXT)
            .extractedTotal(DEFAULT_EXTRACTED_TOTAL)
            .extractedTitle(DEFAULT_EXTRACTED_TITLE)
            .ocrConfidence(DEFAULT_OCR_CONFIDENCE)
            .ocrProcessingTime(DEFAULT_OCR_PROCESSING_TIME)
            .ocrCompletedAt(Instant.now())
            .approvedBy(DEFAULT_APPROVER_ID)
            .approvalDecision(ApprovalDecision.APPROVED)
            .approvalReason("All validation checks passed")
            .approvedAt(Instant.now())
            .build();
    }

    /**
     * Builder for creating customized CreateBillCommand instances.
     */
    public static class CreateBillCommandBuilder {
        private String billId = DEFAULT_BILL_ID;
        private String title = DEFAULT_TITLE;
        private BigDecimal total = DEFAULT_TOTAL;
        private Map<String, String> metadata = Map.of("vendor", "Test Corp");

        public CreateBillCommandBuilder withBillId(String billId) {
            this.billId = billId;
            return this;
        }

        public CreateBillCommandBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public CreateBillCommandBuilder withTotal(BigDecimal total) {
            this.total = total;
            return this;
        }

        public CreateBillCommandBuilder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public CreateBillCommand build() {
            return CreateBillCommand.create(billId, title, total, metadata);
        }
    }

    /**
     * Builder for creating customized BillProjection instances.
     */
    public static class BillProjectionBuilder {
        private String billId = DEFAULT_BILL_ID;
        private String title = DEFAULT_TITLE;
        private BigDecimal total = DEFAULT_TOTAL;
        private Map<String, String> metadata = Map.of("vendor", "Test Corp");
        private BillStatus status = BillStatus.CREATED;
        private Instant createdAt = Instant.now();

        public BillProjectionBuilder withBillId(String billId) {
            this.billId = billId;
            return this;
        }

        public BillProjectionBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public BillProjectionBuilder withTotal(BigDecimal total) {
            this.total = total;
            return this;
        }

        public BillProjectionBuilder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public BillProjectionBuilder withStatus(BillStatus status) {
            this.status = status;
            return this;
        }

        public BillProjectionBuilder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public BillProjectionBuilder asApproved() {
            this.status = BillStatus.APPROVED;
            return this;
        }

        public BillProjectionBuilder asRejected() {
            this.status = BillStatus.REJECTED;
            return this;
        }

        public BillProjection build() {
            return BillProjection.builder()
                .billId(billId)
                .title(title)
                .total(total)
                .metadata(metadata)
                .status(status)
                .createdAt(createdAt)
                .build();
        }
    }

    /**
     * Utility method to generate random UUID for testing.
     */
    public static String generateBillId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Utility method to generate random timestamp for testing.
     */
    public static Instant generateTimestamp() {
        return Instant.now();
    }

    /**
     * Utility method to convert LocalDateTime to Instant for testing.
     */
    public static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
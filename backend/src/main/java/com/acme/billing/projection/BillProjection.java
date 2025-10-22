package com.acme.billing.projection;

import com.acme.billing.domain.BillStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * JPA projection entity for Bill read model.
 * This entity represents the query side of the CQRS pattern and is optimized for reading bill data.
 */
@Entity
@Table(name = "bill_read_model", indexes = {
    @Index(name = "idx_bill_status", columnList = "status"),
    @Index(name = "idx_bill_created_at", columnList = "createdAt"),
    @Index(name = "idx_bill_updated_at", columnList = "updatedAt"),
    @Index(name = "idx_bill_approver", columnList = "approverId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillProjection {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "total", precision = 19, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ocr_extracted_text", columnDefinition = "TEXT")
    private String ocrExtractedText;

    @Column(name = "ocr_extracted_total", precision = 19, scale = 2)
    private BigDecimal ocrExtractedTotal;

    @Column(name = "ocr_extracted_title", length = 255)
    private String ocrExtractedTitle;

    @Column(name = "ocr_confidence", length = 50)
    private String ocrConfidence;

    @Column(name = "ocr_processing_time", length = 50)
    private String ocrProcessingTime;

    @Column(name = "approver_id", length = 100)
    private String approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_decision", length = 20)
    private ApprovalDecision approvalDecision;

    @Column(name = "approval_reason", length = 500)
    private String approvalReason;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        version++;
    }

    /**
     * Represents the approval decision for a bill.
     */
    public enum ApprovalDecision {
        APPROVED,
        REJECTED
    }

    /**
     * Updates the bill status and ensures consistency with the approval decision.
     */
    public void updateStatus(BillStatus newStatus) {
        this.status = newStatus;

        // Auto-set approval decision based on status
        if (newStatus == BillStatus.APPROVED) {
            this.approvalDecision = ApprovalDecision.APPROVED;
        } else if (newStatus == BillStatus.REJECTED) {
            this.approvalDecision = ApprovalDecision.REJECTED;
        }
    }

    /**
     * Checks if the bill is in a terminal state.
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * Checks if OCR processing has been completed.
     */
    public boolean hasOcrResults() {
        return ocrExtractedText != null || ocrExtractedTotal != null || ocrExtractedTitle != null;
    }

    /**
     * Gets the effective total amount, prioritizing OCR-extracted total if available.
     */
    public BigDecimal getEffectiveTotal() {
        return ocrExtractedTotal != null ? ocrExtractedTotal : total;
    }

    /**
     * Gets the effective title, prioritizing OCR-extracted title if available.
     */
    public String getEffectiveTitle() {
        return ocrExtractedTitle != null && !ocrExtractedTitle.isBlank() ? ocrExtractedTitle : title;
    }
}
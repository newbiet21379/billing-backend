package com.acme.billing.domain;

/**
 * Represents the current status of a bill in its lifecycle.
 * Bills follow a specific flow: CREATED → FILE_ATTACHED → PROCESSED → APPROVED/REJECTED
 */
public enum BillStatus {
    /**
     * Bill has been created but no file has been attached yet.
     */
    CREATED("Bill created, awaiting file attachment"),

    /**
     * A file has been attached to the bill and OCR processing has been requested.
     */
    FILE_ATTACHED("File attached, OCR processing initiated"),

    /**
     * OCR processing has completed and results have been applied.
     */
    PROCESSED("OCR processing completed, awaiting approval"),

    /**
     * Bill has been approved by an authorized user.
     */
    APPROVED("Bill approved and finalized"),

    /**
     * Bill has been rejected by an authorized user.
     */
    REJECTED("Bill rejected and requires attention");

    private final String description;

    BillStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if the bill is in a terminal state (approved or rejected).
     */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    /**
     * Checks if the bill can have files attached.
     */
    public boolean canAttachFile() {
        return this == CREATED;
    }

    /**
     * Checks if the bill can have OCR results applied.
     */
    public boolean canApplyOcrResult() {
        return this == FILE_ATTACHED;
    }

    /**
     * Checks if the bill can be approved or rejected.
     */
    public boolean canBeApproved() {
        return this == PROCESSED;
    }
}
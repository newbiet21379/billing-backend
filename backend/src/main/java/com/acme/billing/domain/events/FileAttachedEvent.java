package com.acme.billing.domain.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event emitted when a file is attached to a bill.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAttachedEvent {

    private String billId;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String storagePath;
    private String checksum;
    private Instant attachedAt;

    public FileAttachedEvent(String billId, String filename, String contentType,
                            Long fileSize, String storagePath, String checksum) {
        this.billId = billId;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.checksum = checksum;
        this.attachedAt = Instant.now();
    }
}
package com.acme.billing.domain.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event emitted when OCR processing is requested for a bill file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrRequestedEvent {

    private String billId;
    private String filename;
    private String storagePath;
    private Instant requestedAt;

    public OcrRequestedEvent(String billId, String filename, String storagePath) {
        this.billId = billId;
        this.filename = filename;
        this.storagePath = storagePath;
        this.requestedAt = Instant.now();
    }
}
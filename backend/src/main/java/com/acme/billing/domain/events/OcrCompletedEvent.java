package com.acme.billing.domain.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event emitted when OCR processing has completed and results are ready to be applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrCompletedEvent {

    private String billId;
    private String extractedText;
    private BigDecimal extractedTotal;
    private String extractedTitle;
    private String confidence;
    private String processingTime;
    private Instant completedAt;

    public OcrCompletedEvent(String billId, String extractedText, BigDecimal extractedTotal,
                            String extractedTitle, String confidence, String processingTime) {
        this.billId = billId;
        this.extractedText = extractedText;
        this.extractedTotal = extractedTotal;
        this.extractedTitle = extractedTitle;
        this.confidence = confidence;
        this.processingTime = processingTime;
        this.completedAt = Instant.now();
    }
}
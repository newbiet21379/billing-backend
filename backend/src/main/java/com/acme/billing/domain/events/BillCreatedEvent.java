package com.acme.billing.domain.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event emitted when a new bill is created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillCreatedEvent {

    private String billId;
    private String title;
    private BigDecimal total;
    private Map<String, Object> metadata;
    private Instant createdAt;

    public BillCreatedEvent(String billId, String title, BigDecimal total, Map<String, Object> metadata) {
        this.billId = billId;
        this.title = title;
        this.total = total;
        this.metadata = metadata != null ? metadata : Map.of();
        this.createdAt = Instant.now();
    }
}
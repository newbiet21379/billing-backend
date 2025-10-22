package com.acme.billing.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillSummaryResponse {

    private String billId;

    private String title;

    private BigDecimal total;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String createdBy;

    private BillStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    private String approvedBy;

    private ApprovalDecision approvalDecision;

    private Boolean hasAttachments;

    private Boolean hasOcrResults;

    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    public enum BillStatus {
        PENDING,
        PROCESSING,
        APPROVED,
        REJECTED
    }

    public enum ApprovalDecision {
        APPROVED,
        REJECTED
    }
}
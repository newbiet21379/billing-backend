package com.acme.billing.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillResponse {

    private String billId;

    private String title;

    private BigDecimal total;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String createdBy;

    private BillStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    private String approvedBy;

    private ApprovalDecision approvalDecision;

    private String approvalReason;

    private List<FileInfo> attachments;

    private OcrResult ocrResult;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileInfo {
        private String filename;
        private String contentType;
        private Long fileSize;
        private String storagePath;
        private String checksum;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OcrResult {
        private String extractedText;
        private BigDecimal extractedTotal;
        private String extractedTitle;
        private String confidence;
        private String processingTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime processedAt;
    }
}
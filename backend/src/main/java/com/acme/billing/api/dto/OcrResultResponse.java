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
public class OcrResultResponse {

    private String billId;

    private String extractedText;

    private BigDecimal extractedTotal;

    private String extractedTitle;

    private String confidence;

    private String processingTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;

    private String filename;

    private OcrStatus status;

    private String errorMessage;

    @Builder.Default
    private Map<String, Object> additionalFields = Map.of();

    public enum OcrStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
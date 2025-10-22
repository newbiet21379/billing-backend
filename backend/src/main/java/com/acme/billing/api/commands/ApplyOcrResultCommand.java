package com.acme.billing.api.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyOcrResultCommand {

    @TargetAggregateIdentifier
    @NotBlank(message = "Bill ID is required")
    @Size(max = 36, message = "Bill ID must be at most 36 characters")
    private String billId;

    @NotBlank(message = "Extracted text is required")
    @Size(max = 10000, message = "Extracted text must be at most 10000 characters")
    private String extractedText;

    private BigDecimal extractedTotal;

    @Size(max = 255, message = "Extracted title must be at most 255 characters")
    private String extractedTitle;

    private String confidence;

    private String processingTime;

    @JsonCreator
    public static ApplyOcrResultCommand create(
            @JsonProperty("billId") String billId,
            @JsonProperty("extractedText") String extractedText,
            @JsonProperty("extractedTotal") BigDecimal extractedTotal,
            @JsonProperty("extractedTitle") String extractedTitle,
            @JsonProperty("confidence") String confidence,
            @JsonProperty("processingTime") String processingTime) {
        return ApplyOcrResultCommand.builder()
                .billId(billId)
                .extractedText(extractedText)
                .extractedTotal(extractedTotal)
                .extractedTitle(extractedTitle)
                .confidence(confidence)
                .processingTime(processingTime)
                .build();
    }
}
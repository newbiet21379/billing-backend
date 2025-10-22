package com.acme.billing.api.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBillCommand {

    @TargetAggregateIdentifier
    @NotBlank(message = "Bill ID is required")
    @Size(max = 36, message = "Bill ID must be at most 36 characters")
    private String billId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total amount must be greater than 0")
    private BigDecimal total;

    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @JsonCreator
    public static CreateBillCommand create(
            @JsonProperty("billId") String billId,
            @JsonProperty("title") String title,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        return CreateBillCommand.builder()
                .billId(billId)
                .title(title)
                .total(total)
                .metadata(metadata != null ? metadata : Map.of())
                .build();
    }
}
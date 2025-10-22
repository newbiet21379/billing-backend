package com.acme.billing.api.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveBillCommand {

    @TargetAggregateIdentifier
    @NotBlank(message = "Bill ID is required")
    @Size(max = 36, message = "Bill ID must be at most 36 characters")
    private String billId;

    @NotBlank(message = "Approver ID is required")
    @Size(max = 36, message = "Approver ID must be at most 36 characters")
    private String approverId;

    @NotNull(message = "Decision is required")
    private ApprovalDecision decision;

    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    public enum ApprovalDecision {
        APPROVED,
        REJECTED
    }

    @JsonCreator
    public static ApproveBillCommand create(
            @JsonProperty("billId") String billId,
            @JsonProperty("approverId") String approverId,
            @JsonProperty("decision") ApprovalDecision decision,
            @JsonProperty("reason") String reason) {
        return ApproveBillCommand.builder()
                .billId(billId)
                .approverId(approverId)
                .decision(decision)
                .reason(reason)
                .build();
    }
}
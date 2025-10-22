package com.acme.billing.domain.events;

import com.acme.billing.api.commands.ApproveBillCommand;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event emitted when a bill is approved or rejected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillApprovedEvent {

    private String billId;
    private String approverId;
    private ApproveBillCommand.ApprovalDecision decision;
    private String reason;
    private Instant approvedAt;

    public BillApprovedEvent(String billId, String approverId,
                            ApproveBillCommand.ApprovalDecision decision, String reason) {
        this.billId = billId;
        this.approverId = approverId;
        this.decision = decision;
        this.reason = reason;
        this.approvedAt = Instant.now();
    }
}
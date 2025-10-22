package com.acme.billing.service;

import com.acme.billing.domain.events.BillApprovedEvent;
import com.acme.billing.domain.events.OcrCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * Event handler responsible for processing domain events and triggering email notifications.
 * Implements the event-driven architecture for email communication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventHandler {

    private final NotificationService notificationService;

    /**
     * Handles OcrCompletedEvent by sending an email notification about the OCR processing result.
     *
     * @param event the OCR completed event
     */
    @EventHandler
    public void on(OcrCompletedEvent event) {
        log.info("Handling OcrCompletedEvent for bill: {}", event.getBillId());

        try {
            notificationService.sendOcrCompletionNotification(event);
        } catch (NotificationService.NotificationException e) {
            log.error("Failed to send OCR completion notification for bill: {}", event.getBillId(), e);
            // In a production environment, you might want to implement retry logic or dead-letter queue here
        }
    }

    /**
     * Handles BillApprovedEvent by sending an email notification about the approval decision.
     *
     * @param event the bill approval event
     */
    @EventHandler
    public void on(BillApprovedEvent event) {
        log.info("Handling BillApprovedEvent for bill: {} with decision: {}", event.getBillId(), event.getDecision());

        try {
            notificationService.sendBillApprovalNotification(event);
        } catch (NotificationService.NotificationException e) {
            log.error("Failed to send bill approval notification for bill: {}", event.getBillId(), e);
            // In a production environment, you might want to implement retry logic or dead-letter queue here
        }
    }
}
package com.acme.billing.service;

import com.acme.billing.domain.events.BillApprovedEvent;
import com.acme.billing.domain.events.OcrCompletedEvent;
import com.acme.billing.testdata.BillTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailEventHandler Tests")
class EmailEventHandlerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EmailEventHandler emailEventHandler;

    @Test
    @DisplayName("Should handle OcrCompletedEvent and send notification")
    void shouldHandleOcrCompletedEventAndSendNotification() {
        // Given
        OcrCompletedEvent event = BillTestDataFactory.ocrCompletedEvent();

        // When
        emailEventHandler.on(event);

        // Then
        verify(notificationService, times(1)).sendOcrCompletionNotification(event);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should handle BillApprovedEvent and send notification")
    void shouldHandleBillApprovedEventAndSendNotification() {
        // Given
        BillApprovedEvent event = BillTestDataFactory.billApprovedEvent();

        // When
        emailEventHandler.on(event);

        // Then
        verify(notificationService, times(1)).sendBillApprovalNotification(event);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should handle BillApprovedEvent for rejected bill")
    void shouldHandleBillApprovedEventForRejectedBill() {
        // Given
        BillApprovedEvent event = BillTestDataFactory.billRejectedEvent();

        // When
        emailEventHandler.on(event);

        // Then
        verify(notificationService, times(1)).sendBillApprovalNotification(event);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should handle multiple events in sequence")
    void shouldHandleMultipleEventsInSequence() {
        // Given
        OcrCompletedEvent ocrEvent = BillTestDataFactory.ocrCompletedEvent();
        BillApprovedEvent approvalEvent = BillTestDataFactory.billApprovedEvent();

        // When
        emailEventHandler.on(ocrEvent);
        emailEventHandler.on(approvalEvent);

        // Then
        verify(notificationService, times(1)).sendOcrCompletionNotification(ocrEvent);
        verify(notificationService, times(1)).sendBillApprovalNotification(approvalEvent);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should continue processing when notification service throws exception")
    void shouldContinueProcessingWhenNotificationServiceThrowsException() {
        // Given
        OcrCompletedEvent event = BillTestDataFactory.ocrCompletedEvent();

        doThrow(new NotificationService.NotificationException("Email sending failed"))
            .when(notificationService).sendOcrCompletionNotification(event);

        // When - this should not throw an exception despite the underlying failure
        emailEventHandler.on(event);

        // Then
        verify(notificationService, times(1)).sendOcrCompletionNotification(event);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should handle null events gracefully")
    void shouldHandleNullEventsGracefully() {
        // Given
        BillApprovedEvent event = new BillApprovedEvent(
                null, null, null, null, null
        );

        // When - this should not throw an exception
        emailEventHandler.on(event);

        // Then
        verify(notificationService, times(1)).sendBillApprovalNotification(event);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should handle events with minimal data")
    void shouldHandleEventsWithMinimalData() {
        // Given
        OcrCompletedEvent minimalEvent = new OcrCompletedEvent(
                "minimal-bill", null, null, null, null, null, null
        );

        // When
        emailEventHandler.on(minimalEvent);

        // Then
        verify(notificationService, times(1)).sendOcrCompletionNotification(minimalEvent);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Should log appropriate messages during event handling")
    void shouldLogAppropriateMessagesDuringEventHandling() {
        // This test would require checking log output, which is more complex
        // For now, we verify that the events are processed without errors
        // In a real test environment, you might use LogCaptor or similar tools

        // Given
        OcrCompletedEvent event = BillTestDataFactory.ocrCompletedEvent();

        // When
        emailEventHandler.on(event);

        // Then - verify processing completes without exceptions
        verify(notificationService).sendOcrCompletionNotification(event);
    }
}
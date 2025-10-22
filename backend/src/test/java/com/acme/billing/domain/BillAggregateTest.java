package com.acme.billing.domain;

import com.acme.billing.api.commands.*;
import com.acme.billing.domain.events.*;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for BillAggregate using Axon TestFixture.
 */
class BillAggregateTest {

    private static final String BILL_ID = "bill-123";
    private static final String TITLE = "Test Bill";
    private static final BigDecimal TOTAL = new BigDecimal("150.75");
    private static final String APPROVER_ID = "approver-456";
    private static final String FILENAME = "invoice.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final Long FILE_SIZE = 1024L;
    private static final String STORAGE_PATH = "/bills/invoice.pdf";
    private static final String CHECKSUM = "abc123";

    private FixtureConfiguration<BillAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(BillAggregate.class);
    }

    @Test
    @DisplayName("Should create bill when valid CreateBillCommand is received")
    void shouldCreateBillWhenValidCommand() {
        CreateBillCommand command = CreateBillCommand.create(
            BILL_ID, TITLE, TOTAL, Map.of("vendor", "Test Corp")
        );

        BillCreatedEvent expectedEvent = new BillCreatedEvent(
            BILL_ID, TITLE, TOTAL, Map.of("vendor", "Test Corp")
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals(BILL_ID, aggregate.getBillId());
                assertEquals(TITLE, aggregate.getTitle());
                assertEquals(TOTAL, aggregate.getTotal());
                assertEquals(BillStatus.CREATED, aggregate.getStatus());
                assertNotNull(aggregate.getCreatedAt());
                assertEquals(Map.of("vendor", "Test Corp"), aggregate.getMetadata());
            });
    }

    @Test
    @DisplayName("Should reject CreateBillCommand with null bill ID")
    void shouldRejectCreateBillCommandWithNullBillId() {
        CreateBillCommand command = CreateBillCommand.create(
            null, TITLE, TOTAL, Map.of()
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectException(IllegalArgumentException.class)
            .expectExceptionMessage("Bill ID is required");
    }

    @Test
    @DisplayName("Should reject CreateBillCommand with invalid total amount")
    void shouldRejectCreateBillCommandWithInvalidTotal() {
        CreateBillCommand command = CreateBillCommand.create(
            BILL_ID, TITLE, BigDecimal.ZERO, Map.of()
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectException(IllegalArgumentException.class)
            .expectExceptionMessage("Total amount must be greater than 0");
    }

    @Test
    @DisplayName("Should attach file when valid AttachFileCommand is received")
    void shouldAttachFileWhenValidCommand() {
        CreateBillCommand createCommand = CreateBillCommand.create(BILL_ID, TITLE, TOTAL, Map.of());
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());

        AttachFileCommand attachCommand = AttachFileCommand.create(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );

        BillCreatedEvent expectedCreatedEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());
        FileAttachedEvent expectedFileEvent = new FileAttachedEvent(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );
        OcrRequestedEvent expectedOcrEvent = new OcrRequestedEvent(BILL_ID, FILENAME, STORAGE_PATH);

        fixture.given(createdEvent)
            .when(attachCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedFileEvent, expectedOcrEvent)
            .expectState(aggregate -> {
                assertEquals(FILENAME, aggregate.getFilename());
                assertEquals(CONTENT_TYPE, aggregate.getContentType());
                assertEquals(FILE_SIZE, aggregate.getFileSize());
                assertEquals(STORAGE_PATH, aggregate.getStoragePath());
                assertEquals(CHECKSUM, aggregate.getChecksum());
                assertEquals(BillStatus.FILE_ATTACHED, aggregate.getStatus());
                assertNotNull(aggregate.getFileAttachedAt());
            });
    }

    @Test
    @DisplayName("Should reject file attachment when bill is not in CREATED status")
    void shouldRejectFileAttachmentWhenNotInCreatedStatus() {
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());
        FileAttachedEvent fileAttachedEvent = new FileAttachedEvent(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );

        AttachFileCommand attachCommand = AttachFileCommand.create(
            BILL_ID, "another.pdf", CONTENT_TYPE, FILE_SIZE, "/path/file.pdf", CHECKSUM
        );

        fixture.given(createdEvent, fileAttachedEvent)
            .when(attachCommand)
            .expectException(IllegalStateException.class)
            .expectExceptionMessage("Cannot attach file to bill in status: FILE_ATTACHED");
    }

    @Test
    @DisplayName("Should apply OCR results when valid ApplyOcrResultCommand is received")
    void shouldApplyOcrResultsWhenValidCommand() {
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());
        FileAttachedEvent fileAttachedEvent = new FileAttachedEvent(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );
        OcrRequestedEvent ocrRequestedEvent = new OcrRequestedEvent(BILL_ID, FILENAME, STORAGE_PATH);

        ApplyOcrResultCommand ocrCommand = ApplyOcrResultCommand.create(
            BILL_ID, "Extracted text content", new BigDecimal("160.00"),
            "Extracted Title", "95%", "2.5s"
        );

        OcrCompletedEvent expectedEvent = new OcrCompletedEvent(
            BILL_ID, "Extracted text content", new BigDecimal("160.00"),
            "Extracted Title", "95%", "2.5s"
        );

        fixture.given(createdEvent, fileAttachedEvent, ocrRequestedEvent)
            .when(ocrCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals("Extracted text content", aggregate.getExtractedText());
                assertEquals(new BigDecimal("160.00"), aggregate.getExtractedTotal());
                assertEquals("Extracted Title", aggregate.getExtractedTitle());
                assertEquals("95%", aggregate.getOcrConfidence());
                assertEquals("2.5s", aggregate.getOcrProcessingTime());
                assertEquals(BillStatus.PROCESSED, aggregate.getStatus());
                assertNotNull(aggregate.getOcrCompletedAt());
            });
    }

    @Test
    @DisplayName("Should reject OCR results when bill is not in FILE_ATTACHED status")
    void shouldRejectOcrResultsWhenNotInFileAttachedStatus() {
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());

        ApplyOcrResultCommand ocrCommand = ApplyOcrResultCommand.create(
            BILL_ID, "Extracted text", new BigDecimal("160.00"),
            "Title", "95%", "2.5s"
        );

        fixture.given(createdEvent)
            .when(ocrCommand)
            .expectException(IllegalStateException.class)
            .expectExceptionMessage("Cannot apply OCR results to bill in status: CREATED");
    }

    @ParameterizedTest
    @EnumSource(ApproveBillCommand.ApprovalDecision.class)
    @DisplayName("Should approve or reject bill when valid ApproveBillCommand is received")
    void shouldApproveOrRejectBill(ApproveBillCommand.ApprovalDecision decision) {
        // Setup events to get bill to PROCESSED state
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());
        FileAttachedEvent fileAttachedEvent = new FileAttachedEvent(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );
        OcrRequestedEvent ocrRequestedEvent = new OcrRequestedEvent(BILL_ID, FILENAME, STORAGE_PATH);
        OcrCompletedEvent ocrCompletedEvent = new OcrCompletedEvent(
            BILL_ID, "Extracted text", new BigDecimal("160.00"),
            "Title", "95%", "2.5s"
        );

        ApproveBillCommand approveCommand = ApproveBillCommand.create(
            BILL_ID, APPROVER_ID, decision, decision == ApproveBillCommand.ApprovalDecision.APPROVED
                ? "All checks passed"
                : "Incorrect amount detected"
        );

        BillApprovedEvent expectedEvent = new BillApprovedEvent(
            BILL_ID, APPROVER_ID, decision, decision == ApproveBillCommand.ApprovalDecision.APPROVED
                ? "All checks passed"
                : "Incorrect amount detected"
        );

        BillStatus expectedStatus = decision == ApproveBillCommand.ApprovalDecision.APPROVED
            ? BillStatus.APPROVED
            : BillStatus.REJECTED;

        fixture.given(createdEvent, fileAttachedEvent, ocrRequestedEvent, ocrCompletedEvent)
            .when(approveCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals(APPROVER_ID, aggregate.getApproverId());
                assertEquals(decision, aggregate.getApprovalDecision());
                assertEquals(decision == ApproveBillCommand.ApprovalDecision.APPROVED
                    ? "All checks passed"
                    : "Incorrect amount detected", aggregate.getApprovalReason());
                assertEquals(expectedStatus, aggregate.getStatus());
                assertNotNull(aggregate.getApprovedAt());
            });
    }

    @Test
    @DisplayName("Should reject approval when bill is not in PROCESSED status")
    void shouldRejectApprovalWhenNotInProcessedStatus() {
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());

        ApproveBillCommand approveCommand = ApproveBillCommand.create(
            BILL_ID, APPROVER_ID, ApproveBillCommand.ApprovalDecision.APPROVED, "Approve"
        );

        fixture.given(createdEvent)
            .when(approveCommand)
            .expectException(IllegalStateException.class)
            .expectExceptionMessage("Cannot approve bill in status: CREATED");
    }

    @Test
    @DisplayName("Should reconstruct aggregate state from sequence of events")
    void shouldReconstructAggregateStateFromEvents() {
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of("vendor", "Test"));
        FileAttachedEvent fileAttachedEvent = new FileAttachedEvent(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );
        OcrCompletedEvent ocrCompletedEvent = new OcrCompletedEvent(
            BILL_ID, "Extracted text", new BigDecimal("160.00"),
            "Extracted Title", "95%", "2.5s"
        );
        BillApprovedEvent approvedEvent = new BillApprovedEvent(
            BILL_ID, APPROVER_ID, ApproveBillCommand.ApprovalDecision.APPROVED, "Approved"
        );

        fixture.given(createdEvent, fileAttachedEvent, ocrCompletedEvent, approvedEvent)
            .expectState(aggregate -> {
                // Verify all state is correctly reconstructed
                assertEquals(BILL_ID, aggregate.getBillId());
                assertEquals(TITLE, aggregate.getTitle());
                assertEquals(TOTAL, aggregate.getTotal());
                assertEquals(Map.of("vendor", "Test"), aggregate.getMetadata());
                assertEquals(BillStatus.APPROVED, aggregate.getStatus());
                assertNotNull(aggregate.getCreatedAt());

                // File attachment state
                assertEquals(FILENAME, aggregate.getFilename());
                assertEquals(CONTENT_TYPE, aggregate.getContentType());
                assertEquals(FILE_SIZE, aggregate.getFileSize());
                assertEquals(STORAGE_PATH, aggregate.getStoragePath());
                assertEquals(CHECKSUM, aggregate.getChecksum());
                assertNotNull(aggregate.getFileAttachedAt());

                // OCR state
                assertEquals("Extracted text", aggregate.getExtractedText());
                assertEquals(new BigDecimal("160.00"), aggregate.getExtractedTotal());
                assertEquals("Extracted Title", aggregate.getExtractedTitle());
                assertEquals("95%", aggregate.getOcrConfidence());
                assertEquals("2.5s", aggregate.getOcrProcessingTime());
                assertNotNull(aggregate.getOcrCompletedAt());

                // Approval state
                assertEquals(APPROVER_ID, aggregate.getApproverId());
                assertEquals(ApproveBillCommand.ApprovalDecision.APPROVED, aggregate.getApprovalDecision());
                assertEquals("Approved", aggregate.getApprovalReason());
                assertNotNull(aggregate.getApprovedAt());
            });
    }

    @Test
    @DisplayName("Should handle bill lifecycle flow correctly")
    void shouldHandleBillLifecycleFlowCorrectly() {
        // Step 1: Create bill
        CreateBillCommand createCommand = CreateBillCommand.create(BILL_ID, TITLE, TOTAL, Map.of());
        BillCreatedEvent createdEvent = new BillCreatedEvent(BILL_ID, TITLE, TOTAL, Map.of());

        fixture.givenNoPriorActivity()
            .when(createCommand)
            .expectEvents(createdEvent)
            .expectState(aggregate -> assertEquals(BillStatus.CREATED, aggregate.getStatus()));

        // Step 2: Attach file
        AttachFileCommand attachCommand = AttachFileCommand.create(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );
        FileAttachedEvent fileAttachedEvent = new FileAttachedEvent(
            BILL_ID, FILENAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, CHECKSUM
        );
        OcrRequestedEvent ocrRequestedEvent = new OcrRequestedEvent(BILL_ID, FILENAME, STORAGE_PATH);

        fixture.given(createdEvent)
            .when(attachCommand)
            .expectEvents(fileAttachedEvent, ocrRequestedEvent)
            .expectState(aggregate -> assertEquals(BillStatus.FILE_ATTACHED, aggregate.getStatus()));

        // Step 3: Apply OCR results
        ApplyOcrResultCommand ocrCommand = ApplyOcrResultCommand.create(
            BILL_ID, "Extracted text", new BigDecimal("160.00"),
            "Title", "95%", "2.5s"
        );
        OcrCompletedEvent ocrCompletedEvent = new OcrCompletedEvent(
            BILL_ID, "Extracted text", new BigDecimal("160.00"),
            "Title", "95%", "2.5s"
        );

        fixture.given(createdEvent, fileAttachedEvent, ocrRequestedEvent)
            .when(ocrCommand)
            .expectEvents(ocrCompletedEvent)
            .expectState(aggregate -> assertEquals(BillStatus.PROCESSED, aggregate.getStatus()));

        // Step 4: Approve bill
        ApproveBillCommand approveCommand = ApproveBillCommand.create(
            BILL_ID, APPROVER_ID, ApproveBillCommand.ApprovalDecision.APPROVED, "Approved"
        );
        BillApprovedEvent approvedEvent = new BillApprovedEvent(
            BILL_ID, APPROVER_ID, ApproveBillCommand.ApprovalDecision.APPROVED, "Approved"
        );

        fixture.given(createdEvent, fileAttachedEvent, ocrRequestedEvent, ocrCompletedEvent)
            .when(approveCommand)
            .expectEvents(approvedEvent)
            .expectState(aggregate -> assertEquals(BillStatus.APPROVED, aggregate.getStatus()));
    }
}
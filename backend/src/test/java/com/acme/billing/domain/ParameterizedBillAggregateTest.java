package com.acme.billing.domain;

import com.acme.billing.api.commands.*;
import com.acme.billing.domain.events.*;
import com.acme.billing.testdata.BillTestDataFactory;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Parameterized tests for BillAggregate covering edge cases and boundary conditions.
 * Tests various combinations of inputs and invalid scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Parameterized Bill Aggregate Tests")
class ParameterizedBillAggregateTest {

    private FixtureConfiguration<BillAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(BillAggregate.class);
    }

    // === CreateBillCommand Parameterized Tests ===

    @ParameterizedTest
    @ValueSource(strings = {
        "Valid Bill Title",
        "Bill with numbers 123",
        "Special chars !@#$%^&*()",
        "Unicode test: 你好",
        "Very long title that exceeds normal length but should still be valid for testing purposes",
        "SingleWord",
        "Title with-multiple-dashes_and_underscores"
    })
    @DisplayName("Should accept valid bill titles")
    void shouldAcceptValidBillTitles(String validTitle) {
        CreateBillCommand command = CreateBillCommand.create(
            UUID.randomUUID().toString(),
            validTitle,
            new BigDecimal("100.00"),
            Map.of()
        );

        BillCreatedEvent expectedEvent = new BillCreatedEvent(
            command.getBillId(),
            validTitle,
            new BigDecimal("100.00"),
            Map.of()
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals(validTitle, aggregate.getTitle());
                assertEquals(new BigDecimal("100.00"), aggregate.getTotal());
                assertEquals(BillStatus.CREATED, aggregate.getStatus());
            });
    }

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"   ", "\t", "\n", ""})
    @DisplayName("Should reject invalid bill titles")
    void shouldRejectInvalidBillTitles(String invalidTitle) {
        CreateBillCommand command = CreateBillCommand.create(
            UUID.randomUUID().toString(),
            invalidTitle,
            new BigDecimal("100.00"),
            Map.of()
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectException(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "100.00, true",
        "0.01, true",
        "999999.99, true",
        "0.00, false",
        "-1.00, false",
        "-100.00, false"
    })
    @DisplayName("Should validate total amount correctly")
    void shouldValidateTotalAmountCorrectly(BigDecimal total, boolean shouldBeValid) {
        CreateBillCommand command = CreateBillCommand.create(
            UUID.randomUUID().toString(),
            "Test Bill",
            total,
            Map.of()
        );

        if (shouldBeValid) {
            BillCreatedEvent expectedEvent = new BillCreatedEvent(
                command.getBillId(),
                "Test Bill",
                total,
                Map.of()
            );

            fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(expectedEvent);
        } else {
            fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should reject invalid bill IDs")
    void shouldRejectInvalidBillIds(String invalidBillId) {
        CreateBillCommand command = CreateBillCommand.create(
            invalidBillId,
            "Test Bill",
            new BigDecimal("100.00"),
            Map.of()
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectException(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "valid-id-123",
        "UUID-" + UUID.randomUUID() + "-suffix",
        "12345",
        "simple",
        "complex.id.with-many.parts-123"
    })
    @DisplayName("Should accept valid bill IDs")
    void shouldAcceptValidBillIds(String validBillId) {
        CreateBillCommand command = CreateBillCommand.create(
            validBillId,
            "Test Bill",
            new BigDecimal("100.00"),
            Map.of()
        );

        BillCreatedEvent expectedEvent = new BillCreatedEvent(
            validBillId,
            "Test Bill",
            new BigDecimal("100.00"),
            Map.of()
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent);
    }

    // === Metadata Parameterized Tests ===

    @ParameterizedTest
    @MethodSource("validMetadataProvider")
    @DisplayName("Should accept valid metadata")
    void shouldAcceptValidMetadata(Map<String, String> metadata) {
        CreateBillCommand command = CreateBillCommand.create(
            UUID.randomUUID().toString(),
            "Test Bill",
            new BigDecimal("100.00"),
            metadata
        );

        BillCreatedEvent expectedEvent = new BillCreatedEvent(
            command.getBillId(),
            "Test Bill",
            new BigDecimal("100.00"),
            metadata
        );

        fixture.givenNoPriorActivity()
            .when(command)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> assertEquals(metadata, aggregate.getMetadata()));
    }

    static Stream<Arguments> validMetadataProvider() {
        return Stream.of(
            arguments(Map.of()),
            arguments(Map.of("vendor", "Test Corp")),
            arguments(Map.of("category", "utilities", "department", "engineering")),
            arguments(Map.of("key1", "value1", "key2", "value2", "key3", "value3", "key4", "value4")),
            arguments(Map.of("unicode", "测试", "numbers", "123", "special", "!@#$%")),
            arguments(Map.of("emptyValue", "", "spaceValue", " "))
        );
    }

    // === File Attachment Parameterized Tests ===

    @ParameterizedTest
    @ValueSource(strings = {
        "document.pdf",
        "invoice.jpg",
        "bill.png",
        "receipt.jpeg",
        "document-with-dashes.pdf",
        "file_with_underscores.jpg"
    })
    @DisplayName("Should accept valid filenames")
    void shouldAcceptValidFilenames(String validFilename) {
        String billId = UUID.randomUUID().toString();
        CreateBillCommand createCommand = CreateBillCommand.create(billId, "Test Bill", new BigDecimal("100.00"), Map.of());
        BillCreatedEvent createdEvent = new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of());

        AttachFileCommand attachCommand = AttachFileCommand.create(
            billId,
            validFilename,
            "application/pdf",
            1024L,
            "/bills/test.pdf",
            "checksum123"
        );

        FileAttachedEvent expectedFileEvent = new FileAttachedEvent(
            billId, validFilename, "application/pdf", 1024L, "/bills/test.pdf", "checksum123"
        );
        OcrRequestedEvent expectedOcrEvent = new OcrRequestedEvent(billId, validFilename, "/bills/test.pdf");

        fixture.given(createdEvent)
            .when(attachCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedFileEvent, expectedOcrEvent)
            .expectState(aggregate -> {
                assertEquals(validFilename, aggregate.getFilename());
                assertEquals(BillStatus.FILE_ATTACHED, aggregate.getStatus());
            });
    }

    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @ParameterizedTest
    @DisplayName("Should reject invalid filenames")
    void shouldRejectInvalidFilenames(String invalidFilename) {
        String billId = UUID.randomUUID().toString();
        CreateBillCommand createCommand = CreateBillCommand.create(billId, "Test Bill", new BigDecimal("100.00"), Map.of());
        BillCreatedEvent createdEvent = new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of());

        AttachFileCommand attachCommand = AttachFileCommand.create(
            billId,
            invalidFilename,
            "application/pdf",
            1024L,
            "/bills/test.pdf",
            "checksum123"
        );

        fixture.given(createdEvent)
            .when(attachCommand)
            .expectException(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application/pdf",
        "image/jpeg",
        "image/png",
        "application/msword",
        "text/plain"
    })
    @DisplayName("Should accept valid content types")
    void shouldAcceptValidContentTypes(String validContentType) {
        String billId = UUID.randomUUID().toString();
        CreateBillCommand createCommand = CreateBillCommand.create(billId, "Test Bill", new BigDecimal("100.00"), Map.of());
        BillCreatedEvent createdEvent = new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of());

        AttachFileCommand attachCommand = AttachFileCommand.create(
            billId,
            "test.pdf",
            validContentType,
            1024L,
            "/bills/test.pdf",
            "checksum123"
        );

        fixture.given(createdEvent)
            .when(attachCommand)
            .expectSuccessfulHandlerExecution()
            .expectState(aggregate -> {
                assertEquals(validContentType, aggregate.getContentType());
            });
    }

    @ParameterizedTest
    @CsvSource({
        "0, false",
        "1, true",
        "1024, true",
        "1048576, true",  // 1MB
        "10485760, true", // 10MB
        "-1, false"
    })
    @DisplayName("Should validate file size correctly")
    void shouldValidateFileSizeCorrectly(long fileSize, boolean shouldBeValid) {
        String billId = UUID.randomUUID().toString();
        CreateBillCommand createCommand = CreateBillCommand.create(billId, "Test Bill", new BigDecimal("100.00"), Map.of());
        BillCreatedEvent createdEvent = new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of());

        AttachFileCommand attachCommand = AttachFileCommand.create(
            billId,
            "test.pdf",
            "application/pdf",
            fileSize,
            "/bills/test.pdf",
            "checksum123"
        );

        if (shouldBeValid) {
            fixture.given(createdEvent)
                .when(attachCommand)
                .expectSuccessfulHandlerExecution();
        } else {
            fixture.given(createdEvent)
                .when(attachCommand)
                .expectException(IllegalArgumentException.class);
        }
    }

    // === OCR Result Parameterized Tests ===

    @ParameterizedTest
    @ValueSource(strings = {
        "Simple text",
        "Text with numbers 123",
        "Special characters !@#$%^&*()",
        "Unicode content: 你好世界",
        "",
        "   ",
        "Very long extracted text ".repeat(50)
    })
    @DisplayName("Should accept various OCR text contents")
    void shouldAcceptVariousOcrTextContents(String extractedText) {
        String billId = UUID.randomUUID().toString();
        setupBillForOcrTest(billId);

        ApplyOcrResultCommand ocrCommand = ApplyOcrResultCommand.create(
            billId,
            extractedText,
            new BigDecimal("150.00"),
            "Extracted Title",
            "95%",
            "2.5s"
        );

        OcrCompletedEvent expectedEvent = new OcrCompletedEvent(
            billId, extractedText, new BigDecimal("150.00"), "Extracted Title", "95%", "2.5s"
        );

        fixture.given(
            new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
            new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
            new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf")
        )
            .when(ocrCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals(extractedText, aggregate.getExtractedText());
                assertEquals(BillStatus.PROCESSED, aggregate.getStatus());
            });
    }

    @ParameterizedTest
    @CsvSource({
        "0.00, true",
        "100.50, true",
        "999999.99, true",
        "-1.00, true",  // OCR might detect negative amounts (refunds)
        "null, true"
    })
    @DisplayName("Should accept various OCR extracted totals")
    void shouldAcceptVariousOcrExtractedTotals(String totalStr, boolean shouldBeValid) {
        String billId = UUID.randomUUID().toString();
        setupBillForOcrTest(billId);

        BigDecimal extractedTotal = "null".equals(totalStr) ? null : new BigDecimal(totalStr);

        ApplyOcrResultCommand ocrCommand = ApplyOcrResultCommand.create(
            billId,
            "Extracted text",
            extractedTotal,
            "Extracted Title",
            "95%",
            "2.5s"
        );

        if (shouldBeValid) {
            fixture.given(
                new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
                new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
                new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf")
            )
                .when(ocrCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(extractedTotal, aggregate.getExtractedTotal());
                });
        } else {
            fixture.given(
                new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
                new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
                new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf")
            )
                .when(ocrCommand)
                .expectException(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "High",
        "Medium",
        "Low",
        "95%",
        "85.5%",
        "100%",
        "0%",
        "Very High Confidence"
    })
    @DisplayName("Should accept various OCR confidence levels")
    void shouldAcceptVariousOcrConfidenceLevels(String confidence) {
        String billId = UUID.randomUUID().toString();
        setupBillForOcrTest(billId);

        ApplyOcrResultCommand ocrCommand = ApplyOcrResultCommand.create(
            billId,
            "Extracted text",
            new BigDecimal("150.00"),
            "Extracted Title",
            confidence,
            "2.5s"
        );

        fixture.given(
            new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
            new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
            new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf")
        )
            .when(ocrCommand)
            .expectSuccessfulHandlerExecution()
            .expectState(aggregate -> {
                assertEquals(confidence, aggregate.getOcrConfidence());
            });
    }

    // === Approval Parameterized Tests ===

    @ParameterizedTest
    @EnumSource(ApproveBillCommand.ApprovalDecision.class)
    @DisplayName("Should handle all approval decision types")
    void shouldHandleAllApprovalDecisionTypes(ApproveBillCommand.ApprovalDecision decision) {
        String billId = UUID.randomUUID().toString();
        String approverId = "approver-123";

        ApproveBillCommand approveCommand = ApproveBillCommand.create(
            billId,
            approverId,
            decision,
            decision == ApproveBillCommand.ApprovalDecision.APPROVED ? "Valid bill" : "Invalid amount"
        );

        BillApprovedEvent expectedEvent = new BillApprovedEvent(
            billId, approverId, decision,
            decision == ApproveBillCommand.ApprovalDecision.APPROVED ? "Valid bill" : "Invalid amount"
        );

        fixture.given(
            new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
            new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
            new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf"),
            new OcrCompletedEvent(billId, "Text", new BigDecimal("100.00"), "Title", "95%", "2.5s")
        )
            .when(approveCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals(decision, aggregate.getApprovalDecision());
                assertEquals(BillStatus.APPROVED, aggregate.getStatus()); // All decisions result in final state
            });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should handle approval with invalid approver ID")
    void shouldHandleApprovalWithInvalidApproverId(String invalidApproverId) {
        String billId = UUID.randomUUID().toString();

        ApproveBillCommand approveCommand = ApproveBillCommand.create(
            billId,
            invalidApproverId,
            ApproveBillCommand.ApprovalDecision.APPROVED,
            "Valid bill"
        );

        fixture.given(
            new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
            new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
            new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf"),
            new OcrCompletedEvent(billId, "Text", new BigDecimal("100.00"), "Title", "95%", "2.5s")
        )
            .when(approveCommand)
            .expectException(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Valid bill - all checks passed",
        "Amount matches expected value",
        "Approved by system validation",
        "",
        "   ",
        "\tReason with tabs\t",
        "Unicode reason: 批准",
        "Very long approval reason ".repeat(20)
    })
    @DisplayName("Should handle various approval reasons")
    void shouldHandleVariousApprovalReasons(String approvalReason) {
        String billId = UUID.randomUUID().toString();
        String approverId = "approver-123";

        ApproveBillCommand approveCommand = ApproveBillCommand.create(
            billId,
            approverId,
            ApproveBillCommand.ApprovalDecision.APPROVED,
            approvalReason
        );

        BillApprovedEvent expectedEvent = new BillApprovedEvent(
            billId, approverId, ApproveBillCommand.ApprovalDecision.APPROVED, approvalReason
        );

        fixture.given(
            new BillCreatedEvent(billId, "Test Bill", new BigDecimal("100.00"), Map.of()),
            new FileAttachedEvent(billId, "test.pdf", "application/pdf", 1024L, "/bills/test.pdf", "checksum123"),
            new OcrRequestedEvent(billId, "test.pdf", "/bills/test.pdf"),
            new OcrCompletedEvent(billId, "Text", new BigDecimal("100.00"), "Title", "95%", "2.5s")
        )
            .when(approveCommand)
            .expectSuccessfulHandlerExecution()
            .expectEvents(expectedEvent)
            .expectState(aggregate -> {
                assertEquals(approvalReason, aggregate.getApprovalReason());
            });
    }

    // Helper method
    private void setupBillForOcrTest(String billId) {
        // This method can be used to set up common test scenarios for OCR testing
        // Implementation depends on the specific requirements of each test
    }
}
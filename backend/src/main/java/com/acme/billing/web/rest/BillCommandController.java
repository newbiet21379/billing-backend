package com.acme.billing.web.rest;

import com.acme.billing.api.commands.ApproveBillCommand;
import com.acme.billing.api.commands.AttachFileCommand;
import com.acme.billing.api.commands.CreateBillCommand;
import com.acme.billing.metrics.BillProcessingMetrics;
import com.acme.billing.service.StorageService;
import com.acme.billing.service.exception.FileStorageException;
import com.acme.billing.service.exception.FileValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for handling bill-related commands in the CQRS architecture.
 * This controller is responsible for write operations that modify the state of bills.
 */
@Slf4j
@RestController
@RequestMapping("/api/commands/bills")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bill Commands", description = "API endpoints for bill command operations")
public class BillCommandController {

    private final CommandGateway commandGateway;
    private final StorageService storageService;
    private final BillProcessingMetrics metrics;

    /**
     * Creates a new bill with the provided details.
     *
     * @param createBillCommand the command containing bill creation details
     * @return ResponseEntity with the created bill ID
     */
    @Operation(
        summary = "Create a new bill",
        description = "Creates a new bill with the specified title, total amount, and optional metadata"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Bill creation accepted",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CreateBillResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Business rule violation",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<CreateBillResponse>> createBill(
            @Valid @RequestBody CreateBillCommand createBillCommand) {

        io.micrometer.core.instrument.Timer.Sample timerSample = metrics.startBillCreationTimer();

        log.info("Received request to create bill with title: {}", createBillCommand.getTitle());

        // Generate bill ID if not provided
        if (createBillCommand.getBillId() == null || createBillCommand.getBillId().trim().isEmpty()) {
            createBillCommand.setBillId(UUID.randomUUID().toString());
        }

        metrics.recordBillCreated();

        return commandGateway.send(createBillCommand)
                .thenApply(result -> {
                    log.info("Bill creation command accepted for bill ID: {}", createBillCommand.getBillId());
                    metrics.recordCommandProcessed("CreateBillCommand", "success");
                    metrics.recordBillCreationTime(timerSample);
                    return ResponseEntity
                            .accepted()
                            .body(CreateBillResponse.builder()
                                    .billId(createBillCommand.getBillId())
                                    .message("Bill creation accepted")
                                    .build());
                })
                .exceptionally(throwable -> {
                    log.error("Failed to create bill", throwable);
                    metrics.recordCommandProcessed("CreateBillCommand", "failure");
                    throw new CommandExecutionException("Failed to create bill", throwable);
                });
    }

    /**
     * Uploads a file attachment for an existing bill.
     *
     * @param billId the ID of the bill to attach the file to
     * @param file the file to upload
     * @return ResponseEntity indicating the upload status
     */
    @Operation(
        summary = "Attach file to bill",
        description = "Uploads and attaches a file to an existing bill, triggering OCR processing"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "File upload accepted",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = FileUploadResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid file or bill ID",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Bill not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "413",
            description = "File too large",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    @PostMapping(
        path = "/{billId}/file",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<FileUploadResponse>> attachFile(
            @Parameter(description = "ID of the bill to attach file to", required = true)
            @PathVariable
            @NotBlank(message = "Bill ID is required")
            @Size(max = 36, message = "Bill ID must be at most 36 characters")
            String billId,

            @Parameter(description = "File to upload", required = true)
            @RequestParam("file")
            @Valid
            MultipartFile file) {

        log.info("Received request to attach file to bill: {}, filename: {}, size: {} bytes",
                billId, file.getOriginalFilename(), file.getSize());

        try {
            // Upload file to storage
            String storagePath = storageService.uploadFile(file, billId);

            // Create and send AttachFileCommand
            AttachFileCommand attachFileCommand = AttachFileCommand.builder()
                    .billId(billId)
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .storagePath(storagePath)
                    .build();

            return commandGateway.send(attachFileCommand)
                    .thenApply(result -> {
                        log.info("File attachment command accepted for bill: {}, file: {}",
                                billId, file.getOriginalFilename());
                        return ResponseEntity
                                .accepted()
                                .body(FileUploadResponse.builder()
                                        .billId(billId)
                                        .filename(file.getOriginalFilename())
                                        .storagePath(storagePath)
                                        .fileSize(file.getSize())
                                        .message("File upload accepted and OCR processing initiated")
                                        .build());
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to attach file to bill: {}", billId, throwable);
                        // Clean up uploaded file if command failed
                        try {
                            storageService.deleteFile(storagePath);
                        } catch (Exception cleanupException) {
                            log.warn("Failed to cleanup file after command failure: {}", storagePath, cleanupException);
                        }
                        throw new CommandExecutionException("Failed to attach file", throwable);
                    });

        } catch (FileValidationException e) {
            log.warn("File validation failed for bill: {}", billId, e);
            throw new FileValidationRuntimeException(e.getMessage(), e);
        } catch (FileStorageException e) {
            log.error("File storage failed for bill: {}", billId, e);
            throw new FileStorageRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Approves an existing bill.
     *
     * @param billId the ID of the bill to approve
     * @param approveRequest the approval request details
     * @return ResponseEntity indicating the approval status
     */
    @Operation(
        summary = "Approve a bill",
        description = "Approves a bill that has been processed and is ready for approval"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Bill approval accepted",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApproveBillResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid approval request",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Bill not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Bill not in approvable state",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    @PostMapping(
        path = "/{billId}/approve",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<ApproveBillResponse>> approveBill(
            @Parameter(description = "ID of the bill to approve", required = true)
            @PathVariable
            @NotBlank(message = "Bill ID is required")
            @Size(max = 36, message = "Bill ID must be at most 36 characters")
            String billId,

            @Valid @RequestBody(required = false)
            ApproveBillRequest approveRequest) {

        log.info("Received request to approve bill: {}", billId);

        // Use default values if request body is null
        String approvedBy = (approveRequest != null && approveRequest.getApprovedBy() != null)
                ? approveRequest.getApprovedBy() : "system";
        String approvalReason = (approveRequest != null && approveRequest.getApprovalReason() != null)
                ? approveRequest.getApprovalReason() : "Approved via API";

        ApproveBillCommand approveBillCommand = ApproveBillCommand.builder()
                .billId(billId)
                .approvedBy(approvedBy)
                .approvalReason(approvalReason)
                .build();

        return commandGateway.send(approveBillCommand)
                .thenApply(result -> {
                    log.info("Bill approval command accepted for bill: {}", billId);
                    return ResponseEntity
                            .accepted()
                            .body(ApproveBillResponse.builder()
                                    .billId(billId)
                                    .approvedBy(approvedBy)
                                    .message("Bill approval accepted")
                                    .build());
                })
                .exceptionally(throwable -> {
                    log.error("Failed to approve bill: {}", billId, throwable);
                    throw new CommandExecutionException("Failed to approve bill", throwable);
                });
    }

    // Response DTOs

    @Schema(description = "Response for bill creation")
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateBillResponse {
        @Schema(description = "ID of the created bill")
        private String billId;

        @Schema(description = "Response message")
        private String message;
    }

    @Schema(description = "Response for file upload")
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FileUploadResponse {
        @Schema(description = "ID of the bill")
        private String billId;

        @Schema(description = "Original filename")
        private String filename;

        @Schema(description = "Storage path")
        private String storagePath;

        @Schema(description = "File size in bytes")
        private Long fileSize;

        @Schema(description = "Response message")
        private String message;
    }

    @Schema(description = "Response for bill approval")
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ApproveBillResponse {
        @Schema(description = "ID of the approved bill")
        private String billId;

        @Schema(description = "User who approved the bill")
        private String approvedBy;

        @Schema(description = "Response message")
        private String message;
    }

    @Schema(description = "Request for bill approval")
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ApproveBillRequest {
        @Schema(description = "User who is approving the bill")
        private String approvedBy;

        @Schema(description = "Reason for approval")
        private String approvalReason;
    }

    // Runtime exceptions for error handling

    public static class CommandExecutionException extends RuntimeException {
        public CommandExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FileValidationRuntimeException extends RuntimeException {
        public FileValidationRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FileStorageRuntimeException extends RuntimeException {
        public FileStorageRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
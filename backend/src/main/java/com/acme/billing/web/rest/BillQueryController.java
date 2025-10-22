package com.acme.billing.web.rest;

import com.acme.billing.api.dto.BillResponse;
import com.acme.billing.api.dto.BillSummaryResponse;
import com.acme.billing.api.dto.PaginatedResponse;
import com.acme.billing.api.queries.FindBillQuery;
import com.acme.billing.api.queries.ListBillsQuery;
import com.acme.billing.domain.BillStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for handling bill-related queries in the CQRS architecture.
 * This controller is responsible for read operations that retrieve bill information.
 */
@Slf4j
@RestController
@RequestMapping("/api/queries/bills")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bill Queries", description = "API endpoints for bill query operations")
public class BillQueryController {

    private final QueryGateway queryGateway;

    /**
     * Retrieves a specific bill by its ID.
     *
     * @param billId the ID of the bill to retrieve
     * @return ResponseEntity containing the bill details
     */
    @Operation(
        summary = "Get bill by ID",
        description = "Retrieves detailed information about a specific bill including attachments and OCR results"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bill found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = BillResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid bill ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Bill not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        )
    })
    @GetMapping(
        path = "/{billId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<BillResponse>> getBillById(
            @Parameter(description = "ID of the bill to retrieve", required = true)
            @PathVariable
            @NotBlank(message = "Bill ID is required")
            @Size(max = 36, message = "Bill ID must be at most 36 characters")
            String billId) {

        log.debug("Received request to get bill by ID: {}", billId);

        FindBillQuery query = new FindBillQuery(billId);

        return queryGateway.query(query, BillResponse.class)
                .thenApply(billResponse -> {
                    log.debug("Successfully retrieved bill: {}", billId);
                    return ResponseEntity.ok(billResponse);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to retrieve bill with ID: {}", billId, throwable);
                    throw new QueryExecutionException("Failed to retrieve bill", throwable);
                });
    }

    /**
     * Retrieves a paginated list of bills with optional filtering.
     *
     * @param page the page number (0-based)
     * @param size the page size
     * @param status optional status filter
     * @param createdBy optional created by filter
     * @param createdAfter optional created after date filter
     * @param createdBefore optional created before date filter
     * @param totalMin optional minimum total amount filter
     * @param totalMax optional maximum total amount filter
     * @param sort optional sort field
     * @param direction optional sort direction (ASC/DESC)
     * @return ResponseEntity containing paginated list of bills
     */
    @Operation(
        summary = "List bills with filtering",
        description = "Retrieves a paginated list of bills with optional filtering by various criteria"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bills retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PaginatedBillResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid query parameters",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        )
    })
    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<PaginatedBillResponse>> listBills(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be non-negative")
            int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int size,

            @Parameter(description = "Filter by bill status")
            @RequestParam(required = false)
            BillStatus status,

            @Parameter(description = "Filter by creator")
            @RequestParam(required = false)
            String createdBy,

            @Parameter(description = "Filter by creation date (after)")
            @RequestParam(required = false)
            LocalDate createdAfter,

            @Parameter(description = "Filter by creation date (before)")
            @RequestParam(required = false)
            LocalDate createdBefore,

            @Parameter(description = "Filter by minimum total amount")
            @RequestParam(required = false)
            @Min(value = 0, message = "Minimum total must be non-negative")
            Double totalMin,

            @Parameter(description = "Filter by maximum total amount")
            @RequestParam(required = false)
            @Min(value = 0, message = "Maximum total must be non-negative")
            Double totalMax,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt")
            String sort,

            @Parameter(description = "Sort direction", example = "DESC")
            @RequestParam(defaultValue = "DESC")
            String direction) {

        log.debug("Received request to list bills with filters - page: {}, size: {}, status: {}, createdBy: {}",
                page, size, status, createdBy);

        ListBillsQuery query = ListBillsQuery.builder()
                .page(page)
                .size(size)
                .status(status)
                .createdBy(createdBy)
                .createdAfter(createdAfter)
                .createdBefore(createdBefore)
                .totalMin(totalMin != null ? java.math.BigDecimal.valueOf(totalMin) : null)
                .totalMax(totalMax != null ? java.math.BigDecimal.valueOf(totalMax) : null)
                .sort(sort)
                .direction(direction)
                .build();

        return queryGateway.query(query, PaginatedResponse.class)
                .thenApply(paginatedResponse -> {
                    // Convert to typed response
                    PaginatedBillResponse response = PaginatedBillResponse.builder()
                            .content((List<BillSummaryResponse>) paginatedResponse.getContent())
                            .page(paginatedResponse.getPage())
                            .size(paginatedResponse.getSize())
                            .totalElements(paginatedResponse.getTotalElements())
                            .totalPages(paginatedResponse.getTotalPages())
                            .first(paginatedResponse.isFirst())
                            .last(paginatedResponse.isLast())
                            .build();

                    log.debug("Successfully retrieved {} bills on page {}",
                            response.getContent().size(), page);
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to list bills", throwable);
                    throw new QueryExecutionException("Failed to list bills", throwable);
                });
    }

    /**
     * Retrieves bills by status.
     *
     * @param status the status to filter by
     * @param page the page number (0-based)
     * @param size the page size
     * @return ResponseEntity containing paginated list of bills with specified status
     */
    @Operation(
        summary = "List bills by status",
        description = "Retrieves a paginated list of bills filtered by their current status"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bills retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PaginatedBillResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid status or query parameters",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        )
    })
    @GetMapping(
        path = "/by-status/{status}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<PaginatedBillResponse>> getBillsByStatus(
            @Parameter(description = "Bill status to filter by", required = true)
            @PathVariable
            BillStatus status,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be non-negative")
            int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int size) {

        log.debug("Received request to list bills by status: {}", status);

        // Delegate to listBills with status filter
        return listBills(page, size, status, null, null, null, null, null, "createdAt", "DESC");
    }

    /**
     * Retrieves bills that are pending approval.
     *
     * @param page the page number (0-based)
     * @param size the page size
     * @return ResponseEntity containing paginated list of pending bills
     */
    @Operation(
        summary = "List bills pending approval",
        description = "Retrieves a paginated list of bills that are in PROCESSED status and awaiting approval"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Pending bills retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PaginatedBillResponse.class)
            )
        )
    })
    @GetMapping(
        path = "/pending-approval",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CompletableFuture<ResponseEntity<PaginatedBillResponse>> getBillsPendingApproval(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be non-negative")
            int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int size) {

        log.debug("Received request to list bills pending approval");

        // Delegate to listBills with PROCESSED status filter
        return listBills(page, size, BillStatus.PROCESSED, null, null, null, null, null, "createdAt", "ASC");
    }

    // Response DTOs

    @Schema(description = "Paginated response for bill listings")
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaginatedBillResponse {
        @Schema(description = "List of bills")
        private List<BillSummaryResponse> content;

        @Schema(description = "Current page number (0-based)")
        private int page;

        @Schema(description = "Page size")
        private int size;

        @Schema(description = "Total number of elements")
        private long totalElements;

        @Schema(description = "Total number of pages")
        private int totalPages;

        @Schema(description = "Whether this is the first page")
        private boolean first;

        @Schema(description = "Whether this is the last page")
        private boolean last;
    }

    // Runtime exceptions for error handling

    public static class QueryExecutionException extends RuntimeException {
        public QueryExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
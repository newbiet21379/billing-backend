package com.acme.billing.projection.handler;

import com.acme.billing.api.dto.BillResponse;
import com.acme.billing.api.dto.BillSummaryResponse;
import com.acme.billing.api.dto.PaginatedResponse;
import com.acme.billing.api.queries.FindBillQuery;
import com.acme.billing.api.queries.ListBillsQuery;
import com.acme.billing.domain.BillStatus;
import com.acme.billing.projection.BillProjection;
import com.acme.billing.projection.BillFileProjection;
import com.acme.billing.projection.repository.BillFileReadRepository;
import com.acme.billing.projection.repository.BillReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Axon query handler responsible for handling bill-related queries and mapping projections to response DTOs.
 * This component bridges the query side of CQRS with the read model repositories.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillQueryHandler {

    private final BillReadRepository billReadRepository;
    private final BillFileReadRepository billFileReadRepository;

    /**
     * Handles FindBillQuery to retrieve a single bill by ID.
     */
    @QueryHandler
    @Transactional(readOnly = true)
    public BillResponse handle(FindBillQuery query) {
        log.debug("Handling FindBillQuery for billId: {}", query.getBillId());

        Optional<BillProjection> billProjection = billReadRepository.findById(query.getBillId());

        if (billProjection.isEmpty()) {
            log.warn("Bill not found with ID: {}", query.getBillId());
            throw new IllegalArgumentException("Bill not found with ID: " + query.getBillId());
        }

        List<BillFileProjection> fileProjections = billFileReadRepository.findByBillId(query.getBillId());

        BillResponse response = mapToBillResponse(billProjection.get(), fileProjections);
        log.debug("Retrieved bill: {}", query.getBillId());

        return response;
    }

    /**
     * Handles ListBillsQuery to retrieve a paginated list of bills with optional filtering.
     */
    @QueryHandler
    @Transactional(readOnly = true)
    public PaginatedResponse<BillSummaryResponse> handle(ListBillsQuery query) {
        log.debug("Handling ListBillsQuery with filters: titleFilter={}, status={}, approverIds={}, " +
                 "minTotal={}, maxTotal={}, createdAfter={}, createdBefore={}, page={}, size={}",
                query.getTitleFilter(), query.getStatus(), query.getApproverIds(),
                query.getMinTotal(), query.getMaxTotal(), query.getCreatedAfter(), query.getCreatedBefore(),
                query.getPage(), query.getSize());

        Pageable pageable = createPageable(query);
        Page<BillProjection> billPage = findBillsWithFilters(query, pageable);

        List<BillSummaryResponse> summaries = billPage.getContent().stream()
                .map(this::mapToBillSummaryResponse)
                .collect(Collectors.toList());

        PaginatedResponse<BillSummaryResponse> response = PaginatedResponse.<BillSummaryResponse>builder()
                .content(summaries)
                .page(billPage.getNumber())
                .size(billPage.getSize())
                .totalElements(billPage.getTotalElements())
                .totalPages(billPage.getTotalPages())
                .first(billPage.isFirst())
                .last(billPage.isLast())
                .build();

        log.debug("Retrieved {} bills out of {} total", summaries.size(), billPage.getTotalElements());
        return response;
    }

    
    /**
     * Maps BillProjection to BillResponse with file attachments.
     */
    private BillResponse mapToBillResponse(BillProjection projection, List<BillFileProjection> fileProjections) {
        return BillResponse.builder()
                .billId(projection.getId())
                .title(projection.getEffectiveTitle())
                .total(projection.getEffectiveTotal())
                .status(mapToApiStatus(projection.getStatus()))
                .createdAt(convertToLocalDateTime(projection.getCreatedAt()))
                .updatedAt(convertToLocalDateTime(projection.getUpdatedAt()))
                .approvedBy(projection.getApproverId())
                .approvalDecision(mapToApiApprovalDecision(projection.getApprovalDecision()))
                .approvalReason(projection.getApprovalReason())
                .approvedAt(convertToLocalDateTime(projection.getApprovedAt()))
                .attachments(fileProjections.stream()
                        .map(this::mapToFileInfo)
                        .collect(Collectors.toList()))
                .ocrResult(projection.hasOcrResults() ? BillResponse.OcrResult.builder()
                        .extractedText(projection.getOcrExtractedText())
                        .extractedTotal(projection.getOcrExtractedTotal())
                        .extractedTitle(projection.getOcrExtractedTitle())
                        .confidence(projection.getOcrConfidence())
                        .processingTime(projection.getOcrProcessingTime())
                        .processedAt(convertToLocalDateTime(projection.getUpdatedAt()))
                        .build() : null)
                .build();
    }

    /**
     * Maps BillProjection to BillSummaryResponse.
     */
    private BillSummaryResponse mapToBillSummaryResponse(BillProjection projection) {
        int fileCount = billFileReadRepository.countByBillId(projection.getId());

        return BillSummaryResponse.builder()
                .billId(projection.getId())
                .title(projection.getEffectiveTitle())
                .total(projection.getEffectiveTotal())
                .status(mapToApiStatus(projection.getStatus()))
                .createdAt(convertToLocalDateTime(projection.getCreatedAt()))
                .approvedBy(projection.getApproverId())
                .approvalDecision(mapToApiApprovalDecision(projection.getApprovalDecision()))
                .hasAttachments(fileCount > 0)
                .hasOcrResults(projection.hasOcrResults())
                .build();
    }

    /**
     * Maps BillFileProjection to BillResponse.FileInfo.
     */
    private BillResponse.FileInfo mapToFileInfo(BillFileProjection fileProjection) {
        return BillResponse.FileInfo.builder()
                .filename(fileProjection.getFilename())
                .contentType(fileProjection.getContentType())
                .fileSize(fileProjection.getFileSize())
                .storagePath(fileProjection.getStoragePath())
                .checksum(fileProjection.getChecksum())
                .uploadedAt(convertToLocalDateTime(fileProjection.getAttachedAt()))
                .build();
    }

    /**
     * Converts domain BillStatus to API BillStatus.
     */
    private BillResponse.BillStatus mapToApiStatus(BillStatus domainStatus) {
        if (domainStatus == null) {
            return null;
        }

        switch (domainStatus) {
            case CREATED:
            case FILE_ATTACHED:
                return BillResponse.BillStatus.PENDING;
            case PROCESSED:
                return BillResponse.BillStatus.PROCESSING;
            case APPROVED:
                return BillResponse.BillStatus.APPROVED;
            case REJECTED:
                return BillResponse.BillStatus.REJECTED;
            default:
                return null;
        }
    }

    /**
     * Converts domain ApprovalDecision to API ApprovalDecision.
     */
    private BillResponse.ApprovalDecision mapToApiApprovalDecision(BillProjection.ApprovalDecision domainDecision) {
        if (domainDecision == null) {
            return null;
        }

        switch (domainDecision) {
            case APPROVED:
                return BillResponse.ApprovalDecision.APPROVED;
            case REJECTED:
                return BillResponse.ApprovalDecision.REJECTED;
            default:
                return null;
        }
    }

    /**
     * Converts Instant to LocalDateTime.
     */
    private java.time.LocalDateTime convertToLocalDateTime(Instant instant) {
        return instant != null ? java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()) : null;
    }

    /**
     * Creates Pageable object from query parameters.
     */
    private Pageable createPageable(ListBillsQuery query) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");

        if (query.getSortBy() != null) {
            Sort.Direction direction = query.getSortDirection() != null
                    ? (query.getSortDirection() == ListBillsQuery.SortDirection.ASC
                            ? Sort.Direction.ASC
                            : Sort.Direction.DESC)
                    : Sort.Direction.DESC;
            sort = Sort.by(direction, query.getSortBy());
        }

        return PageRequest.of(
                query.getPage(),
                query.getSize(),
                sort
        );
    }

    /**
     * Finds bills applying the filters from the ListBillsQuery.
     */
    private Page<BillProjection> findBillsWithFilters(ListBillsQuery query, Pageable pageable) {
        // Convert LocalDateTime to Instant for comparison
        Instant createdAfter = query.getCreatedAfter() != null
                ? query.getCreatedAfter().atZone(ZoneId.systemDefault()).toInstant()
                : null;
        Instant createdBefore = query.getCreatedBefore() != null
                ? query.getCreatedBefore().atZone(ZoneId.systemDefault()).toInstant()
                : null;

        // Handle different filter combinations efficiently
        if (query.getStatus() != null && query.getApproverIds() != null && !query.getApproverIds().isEmpty()) {
            // If we have both status and approver IDs, use a more specific query
            return billReadRepository.findByStatus(query.getStatus() == ListBillsQuery.BillStatus.PENDING
                    ? com.acme.billing.domain.BillStatus.PROCESSED
                    : query.getStatus() == ListBillsQuery.BillStatus.APPROVED
                            ? com.acme.billing.domain.BillStatus.APPROVED
                            : com.acme.billing.domain.BillStatus.REJECTED, pageable)
                    .map(page -> page.filter(bill -> query.getApproverIds().contains(bill.getApproverId())));
        }

        if (query.getStatus() != null) {
            com.acme.billing.domain.BillStatus domainStatus = convertStatus(query.getStatus());
            if (domainStatus != null) {
                return billReadRepository.findByStatus(domainStatus, pageable);
            }
        }

        if (query.getTitleFilter() != null && !query.getTitleFilter().isBlank()) {
            return billReadRepository.findByTitleContainingIgnoreCase(query.getTitleFilter(), pageable);
        }

        if (query.getMinTotal() != null || query.getMaxTotal() != null) {
            BigDecimal minTotal = query.getMinTotal() != null ? query.getMinTotal() : BigDecimal.ZERO;
            BigDecimal maxTotal = query.getMaxTotal() != null ? query.getMaxTotal() : new BigDecimal("999999999.99");
            return billReadRepository.findByTotalBetween(minTotal, maxTotal, pageable);
        }

        if (createdAfter != null || createdBefore != null) {
            if (createdAfter != null && createdBefore != null) {
                return billReadRepository.findByCreatedAtBetween(createdAfter, createdBefore, pageable);
            } else if (createdAfter != null) {
                return billReadRepository.findByCreatedAtBetween(createdAfter, Instant.now(), pageable);
            } else {
                return billReadRepository.findByCreatedAtBetween(Instant.EPOCH, createdBefore, pageable);
            }
        }

        // Default query if no specific filters
        return billReadRepository.findAll(pageable);
    }

    /**
     * Converts ListBillsQuery.BillStatus to domain BillStatus.
     */
    private com.acme.billing.domain.BillStatus convertStatus(ListBillsQuery.BillStatus queryStatus) {
        if (queryStatus == null) {
            return null;
        }

        switch (queryStatus) {
            case PENDING:
                return com.acme.billing.domain.BillStatus.PROCESSED;
            case APPROVED:
                return com.acme.billing.domain.BillStatus.APPROVED;
            case REJECTED:
                return com.acme.billing.domain.BillStatus.REJECTED;
            case PROCESSING:
                return com.acme.billing.domain.BillStatus.FILE_ATTACHED;
            default:
                return null;
        }
    }
}
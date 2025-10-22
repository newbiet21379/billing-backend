package com.acme.billing.api.queries;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListBillsQuery {

    @Min(value = 0, message = "Page number must be 0 or greater")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Builder.Default
    private int size = 20;

    @Size(max = 255, message = "Title filter must be at most 255 characters")
    private String titleFilter;

    private BigDecimal minTotal;

    private BigDecimal maxTotal;

    private BillStatus status;

    private LocalDateTime createdAfter;

    private LocalDateTime createdBefore;

    private LocalDateTime approvedAfter;

    private LocalDateTime approvedBefore;

    private List<String> approverIds;

    private String sortBy;

    @Builder.Default
    private SortDirection sortDirection = SortDirection.ASC;

    public enum BillStatus {
        PENDING,
        APPROVED,
        REJECTED,
        PROCESSING
    }

    public enum SortDirection {
        ASC,
        DESC
    }

    @JsonCreator
    public static ListBillsQuery create(
            @JsonProperty("page") Integer page,
            @JsonProperty("size") Integer size,
            @JsonProperty("titleFilter") String titleFilter,
            @JsonProperty("minTotal") BigDecimal minTotal,
            @JsonProperty("maxTotal") BigDecimal maxTotal,
            @JsonProperty("status") BillStatus status,
            @JsonProperty("createdAfter") LocalDateTime createdAfter,
            @JsonProperty("createdBefore") LocalDateTime createdBefore,
            @JsonProperty("approvedAfter") LocalDateTime approvedAfter,
            @JsonProperty("approvedBefore") LocalDateTime approvedBefore,
            @JsonProperty("approverIds") List<String> approverIds,
            @JsonProperty("sortBy") String sortBy,
            @JsonProperty("sortDirection") SortDirection sortDirection) {
        return ListBillsQuery.builder()
                .page(page != null ? page : 0)
                .size(size != null ? size : 20)
                .titleFilter(titleFilter)
                .minTotal(minTotal)
                .maxTotal(maxTotal)
                .status(status)
                .createdAfter(createdAfter)
                .createdBefore(createdBefore)
                .approvedAfter(approvedAfter)
                .approvedBefore(approvedBefore)
                .approverIds(approverIds)
                .sortBy(sortBy)
                .sortDirection(sortDirection != null ? sortDirection : SortDirection.ASC)
                .build();
    }
}
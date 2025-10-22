package com.acme.billing.web.rest;

import com.acme.billing.api.dto.BillResponse;
import com.acme.billing.api.dto.BillSummaryResponse;
import com.acme.billing.api.dto.PaginatedResponse;
import com.acme.billing.domain.BillStatus;
import com.acme.billing.web.rest.BillQueryController.*;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for BillQueryController.
 * Tests the REST endpoints for bill query operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BillQueryController Tests")
class BillQueryControllerTest {

    @Mock
    private QueryGateway queryGateway;

    @InjectMocks
    private BillQueryController billQueryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(billQueryController).build();
    }

    @Test
    @DisplayName("Should get bill by ID successfully when bill exists")
    void shouldGetBillByIdSuccessfullyWhenBillExists() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        BillResponse billResponse = createSampleBillResponse(billId);

        when(queryGateway.query(any(), eq(BillResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(billResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.title").value("Electric Bill"))
                .andExpect(jsonPath("$.total").value(150.75))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(queryGateway).query(any(), eq(BillResponse.class));
    }

    @Test
    @DisplayName("Should return 404 when getting non-existent bill")
    void shouldReturnNotFoundWhenGettingNonExistentBill() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();

        when(queryGateway.query(any(), eq(BillResponse.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new QueryExecutionException("Bill not found",
                                new NoSuchElementException("Bill with ID " + billId + " not found"))));

        // When & Then
        mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(queryGateway).query(any(), eq(BillResponse.class));
    }

    @Test
    @DisplayName("Should return 400 when getting bill with invalid bill ID")
    void shouldReturnBadRequestWhenGettingBillWithInvalidBillId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/queries/bills/{billId}", "")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(queryGateway, never()).query(any(), any());
    }

    @Test
    @DisplayName("Should list bills with default parameters successfully")
    void shouldListBillsWithDefaultParametersSuccessfully() throws Exception {
        // Given
        List<BillSummaryResponse> bills = List.of(
                createSampleBillSummaryResponse("bill-1", "Electric Bill"),
                createSampleBillSummaryResponse("bill-2", "Gas Bill")
        );

        PaginatedResponse paginatedResponse = PaginatedResponse.builder()
                .content(bills)
                .page(0)
                .size(20)
                .totalElements(2)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(queryGateway.query(any(), eq(PaginatedResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(paginatedResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(queryGateway).query(any(), eq(PaginatedResponse.class));
    }

    @Test
    @DisplayName("Should list bills with filters successfully")
    void shouldListBillsWithFiltersSuccessfully() throws Exception {
        // Given
        List<BillSummaryResponse> bills = List.of(
                createSampleBillSummaryResponse("bill-1", "Electric Bill")
        );

        PaginatedResponse paginatedResponse = PaginatedResponse.builder()
                .content(bills)
                .page(1)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(false)
                .last(true)
                .build();

        when(queryGateway.query(any(), eq(PaginatedResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(paginatedResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills")
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "APPROVED")
                        .param("createdBy", "user@example.com")
                        .param("totalMin", "50")
                        .param("totalMax", "200")
                        .param("sort", "title")
                        .param("direction", "ASC")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        verify(queryGateway).query(any(), eq(PaginatedResponse.class));
    }

    @Test
    @DisplayName("Should return 400 when listing bills with invalid parameters")
    void shouldReturnBadRequestWhenListingBillsWithInvalidParameters() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/queries/bills")
                        .param("page", "-1")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(queryGateway, never()).query(any(), any());
    }

    @Test
    @DisplayName("Should return 400 when listing bills with size over limit")
    void shouldReturnBadRequestWhenListingBillsWithSizeOverLimit() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/queries/bills")
                        .param("size", "101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(queryGateway, never()).query(any(), any());
    }

    @Test
    @DisplayName("Should list bills by status successfully")
    void shouldListBillsByStatusSuccessfully() throws Exception {
        // Given
        List<BillSummaryResponse> bills = List.of(
                createSampleBillSummaryResponse("bill-1", "Electric Bill")
        );

        PaginatedResponse paginatedResponse = PaginatedResponse.builder()
                .content(bills)
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(queryGateway.query(any(), eq(PaginatedResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(paginatedResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills/by-status/{status}", BillStatus.APPROVED)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"));

        verify(queryGateway).query(any(), eq(PaginatedResponse.class));
    }

    @Test
    @DisplayName("Should list bills pending approval successfully")
    void shouldListBillsPendingApprovalSuccessfully() throws Exception {
        // Given
        List<BillSummaryResponse> bills = List.of(
                createSampleBillSummaryResponse("bill-1", "Electric Bill")
        );

        PaginatedResponse paginatedResponse = PaginatedResponse.builder()
                .content(bills)
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(queryGateway.query(any(), eq(PaginatedResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(paginatedResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills/pending-approval")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(queryGateway).query(any(), eq(PaginatedResponse.class));
    }

    @Test
    @DisplayName("Should return 500 when query execution fails")
    void shouldReturnInternalServerErrorWhenQueryExecutionFails() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();

        when(queryGateway.query(any(), eq(BillResponse.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new QueryExecutionException("Database connection failed", new RuntimeException())));

        // When & Then
        mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(queryGateway).query(any(), eq(BillResponse.class));
    }

    @Test
    @DisplayName("Should handle list bills with custom pagination")
    void shouldHandleListBillsWithCustomPagination() throws Exception {
        // Given
        List<BillSummaryResponse> bills = List.of(
                createSampleBillSummaryResponse("bill-1", "Electric Bill")
        );

        PaginatedResponse paginatedResponse = PaginatedResponse.builder()
                .content(bills)
                .page(2)
                .size(50)
                .totalElements(101)
                .totalPages(3)
                .first(false)
                .last(false)
                .build();

        when(queryGateway.query(any(), eq(PaginatedResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(paginatedResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills")
                        .param("page", "2")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        verify(queryGateway).query(any(), eq(PaginatedResponse.class));
    }

    @Test
    @DisplayName("Should handle list bills with date filters")
    void shouldHandleListBillsWithDateFilters() throws Exception {
        // Given
        PaginatedResponse paginatedResponse = PaginatedResponse.builder()
                .content(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();

        when(queryGateway.query(any(), eq(PaginatedResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(paginatedResponse));

        // When & Then
        mockMvc.perform(get("/api/queries/bills")
                        .param("createdAfter", "2023-01-01")
                        .param("createdBefore", "2023-12-31")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(queryGateway).query(any(), eq(PaginatedResponse.class));
    }

    // Helper methods

    private BillResponse createSampleBillResponse(String billId) {
        return BillResponse.builder()
                .billId(billId)
                .title("Electric Bill")
                .total(new BigDecimal("150.75"))
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .createdBy("user@example.com")
                .status(BillResponse.BillStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .approvedBy("manager@example.com")
                .approvalDecision(BillResponse.ApprovalDecision.APPROVED)
                .approvalReason("Approved for payment")
                .metadata(Map.of("category", "utilities"))
                .build();
    }

    private BillSummaryResponse createSampleBillSummaryResponse(String billId, String title) {
        return BillSummaryResponse.builder()
                .billId(billId)
                .title(title)
                .total(new BigDecimal("150.75"))
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .createdBy("user@example.com")
                .status(BillStatus.APPROVED)
                .build();
    }
}
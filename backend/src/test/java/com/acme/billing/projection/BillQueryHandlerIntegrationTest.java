package com.acme.billing.projection;

import com.acme.billing.api.dto.BillResponse;
import com.acme.billing.api.dto.BillSummaryResponse;
import com.acme.billing.api.dto.PaginatedResponse;
import com.acme.billing.api.queries.FindBillQuery;
import com.acme.billing.api.queries.ListBillsQuery;
import com.acme.billing.domain.BillStatus;
import com.acme.billing.projection.handler.BillQueryHandler;
import com.acme.billing.projection.repository.BillFileReadRepository;
import com.acme.billing.projection.repository.BillReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BillQueryHandler.
 * Tests the complete query handling flow with real database and projections.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",
        "logging.level.org.hibernate.SQL=DEBUG"
})
@DisplayName("BillQueryHandler Integration Tests")
class BillQueryHandlerIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BillReadRepository billReadRepository;

    @Autowired
    private BillFileReadRepository billFileReadRepository;

    private BillQueryHandler queryHandler;

    @BeforeEach
    void setUp() {
        queryHandler = new BillQueryHandler(billReadRepository, billFileReadRepository);
    }

    @Test
    @Transactional
    @DisplayName("Should handle FindBillQuery and return bill with files")
    void shouldHandleFindBillQueryAndReturnBillWithFiles() {
        // Given - create bill with files
        String billId = "bill-find-test";
        BillProjection billProjection = createBillProjection(billId, "Test Bill", new BigDecimal("150.00"));
        String fileId1 = createFileProjection(billId, "invoice.pdf", "application/pdf", 2048L);
        String fileId2 = createFileProjection(billId, "receipt.jpg", "image/jpeg", 1024L);

        FindBillQuery query = new FindBillQuery(billId);

        // When
        BillResponse response = queryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(billId, response.getBillId());
        assertEquals("Test Bill", response.getTitle());
        assertEquals(new BigDecimal("150.00"), response.getTotal());
        assertEquals(BillResponse.BillStatus.PENDING, response.getStatus()); // CREATED maps to PENDING
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
        assertEquals(2, response.getAttachments().size());

        // Verify file attachments
        List<String> filenames = response.getAttachments().stream()
                .map(BillResponse.FileInfo::getFilename)
                .toList();
        assertTrue(filenames.contains("invoice.pdf"));
        assertTrue(filenames.contains("receipt.jpg"));
    }

    @Test
    @Transactional
    @DisplayName("Should throw exception for non-existent bill in FindBillQuery")
    void shouldThrowExceptionForNonExistentBillInFindBillQuery() {
        // Given
        FindBillQuery query = new FindBillQuery("non-existent-bill");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> queryHandler.handle(query));
    }

    @Test
    @Transactional
    @DisplayName("Should handle ListBillsQuery without filters")
    void shouldHandleListBillsQueryWithoutFilters() {
        // Given - create multiple bills
        createBillProjection("bill-1", "Bill 1", new BigDecimal("100.00"));
        createBillProjection("bill-2", "Bill 2", new BigDecimal("200.00"));
        createBillProjection("bill-3", "Bill 3", new BigDecimal("300.00"));

        ListBillsQuery query = ListBillsQuery.builder()
                .page(0)
                .size(10)
                .build();

        // When
        PaginatedResponse<BillSummaryResponse> response = queryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(3, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertEquals(3, response.getContent().size());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());

        // Verify content
        List<String> billIds = response.getContent().stream()
                .map(BillSummaryResponse::getBillId)
                .toList();
        assertTrue(billIds.contains("bill-1"));
        assertTrue(billIds.contains("bill-2"));
        assertTrue(billIds.contains("bill-3"));
    }

    @Test
    @Transactional
    @DisplayName("Should handle ListBillsQuery with title filter")
    void shouldHandleListBillsQueryWithTitleFilter() {
        // Given - create bills with different titles
        createBillProjection("bill-1", "Electric Bill", new BigDecimal("100.00"));
        createBillProjection("bill-2", "Water Bill", new BigDecimal("50.00"));
        createBillProjection("bill-3", "Internet Invoice", new BigDecimal("75.00"));
        createBillProjection("bill-4", "Electric Utility", new BigDecimal("150.00"));

        ListBillsQuery query = ListBillsQuery.builder()
                .page(0)
                .size(10)
                .titleFilter("Electric")
                .build();

        // When
        PaginatedResponse<BillSummaryResponse> response = queryHandler.handle(query);

        // Then
        assertEquals(2, response.getTotalElements());
        assertEquals(2, response.getContent().size());

        List<String> titles = response.getContent().stream()
                .map(BillSummaryResponse::getTitle)
                .toList();
        assertTrue(titles.stream().anyMatch(title -> title.contains("Electric")));
    }

    @Test
    @Transactional
    @DisplayName("Should handle ListBillsQuery with status filter")
    void shouldHandleListBillsQueryWithStatusFilter() {
        // Given - create bills with different statuses
        createBillProjectionWithStatus("bill-1", "Pending Bill 1", new BigDecimal("100.00"), BillStatus.PROCESSED);
        createBillProjectionWithStatus("bill-2", "Pending Bill 2", new BigDecimal("200.00"), BillStatus.PROCESSED);
        createBillProjectionWithStatus("bill-3", "Approved Bill", new BigDecimal("300.00"), BillStatus.APPROVED);
        createBillProjectionWithStatus("bill-4", "Rejected Bill", new BigDecimal("150.00"), BillStatus.REJECTED);

        ListBillsQuery query = ListBillsQuery.builder()
                .page(0)
                .size(10)
                .status(ListBillsQuery.BillStatus.PENDING) // Maps to PROCESSED
                .build();

        // When
        PaginatedResponse<BillSummaryResponse> response = queryHandler.handle(query);

        // Then
        assertEquals(2, response.getTotalElements());
        assertEquals(2, response.getContent().size());

        List<String> billIds = response.getContent().stream()
                .map(BillSummaryResponse::getBillId)
                .toList();
        assertTrue(billIds.contains("bill-1"));
        assertTrue(billIds.contains("bill-2"));
    }

    @Test
    @Transactional
    @DisplayName("Should handle ListBillsQuery with total amount filter")
    void shouldHandleListBillsQueryWithTotalAmountFilter() {
        // Given - create bills with different amounts
        createBillProjection("bill-1", "Small Bill", new BigDecimal("50.00"));
        createBillProjection("bill-2", "Medium Bill", new BigDecimal("150.00"));
        createBillProjection("bill-3", "Large Bill", new BigDecimal("350.00"));

        ListBillsQuery query = ListBillsQuery.builder()
                .page(0)
                .size(10)
                .minTotal(new BigDecimal("100.00"))
                .maxTotal(new BigDecimal("200.00"))
                .build();

        // When
        PaginatedResponse<BillSummaryResponse> response = queryHandler.handle(query);

        // Then
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals("Medium Bill", response.getContent().get(0).getTitle());
        assertEquals(new BigDecimal("150.00"), response.getContent().get(0).getTotal());
    }

    @Test
    @Transactional
    @DisplayName("Should handle ListBillsQuery with pagination")
    void shouldHandleListBillsQueryWithPagination() {
        // Given - create multiple bills
        for (int i = 1; i <= 15; i++) {
            createBillProjection("bill-" + i, "Bill " + i, new BigDecimal(i * 10.00));
        }

        // When - first page
        ListBillsQuery firstPageQuery = ListBillsQuery.builder()
                .page(0)
                .size(5)
                .build();
        PaginatedResponse<BillSummaryResponse> firstPage = queryHandler.handle(firstPageQuery);

        // Then - verify first page
        assertEquals(0, firstPage.getPage());
        assertEquals(5, firstPage.getSize());
        assertEquals(15, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertEquals(5, firstPage.getContent().size());
        assertTrue(firstPage.isFirst());
        assertFalse(firstPage.isLast());

        // When - second page
        ListBillsQuery secondPageQuery = ListBillsQuery.builder()
                .page(1)
                .size(5)
                .build();
        PaginatedResponse<BillSummaryResponse> secondPage = queryHandler.handle(secondPageQuery);

        // Then - verify second page
        assertEquals(1, secondPage.getPage());
        assertEquals(5, secondPage.getSize());
        assertEquals(15, secondPage.getTotalElements());
        assertEquals(3, secondPage.getTotalPages());
        assertEquals(5, secondPage.getContent().size());
        assertFalse(secondPage.isFirst());
        assertFalse(secondPage.isLast());

        // When - last page
        ListBillsQuery lastPageQuery = ListBillsQuery.builder()
                .page(2)
                .size(5)
                .build();
        PaginatedResponse<BillSummaryResponse> lastPage = queryHandler.handle(lastPageQuery);

        // Then - verify last page
        assertEquals(2, lastPage.getPage());
        assertEquals(5, lastPage.getSize());
        assertEquals(15, lastPage.getTotalElements());
        assertEquals(3, lastPage.getTotalPages());
        assertEquals(5, lastPage.getContent().size());
        assertFalse(lastPage.isFirst());
        assertTrue(lastPage.isLast());
    }

    @Test
    @Transactional
    @DisplayName("Should handle ListBillsQuery with sorting")
    void shouldHandleListBillsQueryWithSorting() {
        // Given - create bills with different creation times
        Instant now = Instant.now();
        createBillProjectionWithTimestamp("bill-1", "Oldest Bill", new BigDecimal("100.00"), now.minusSeconds(3600));
        createBillProjectionWithTimestamp("bill-2", "Middle Bill", new BigDecimal("200.00"), now.minusSeconds(1800));
        createBillProjectionWithTimestamp("bill-3", "Newest Bill", new BigDecimal("300.00"), now);

        // When - sort by title ascending
        ListBillsQuery query = ListBillsQuery.builder()
                .page(0)
                .size(10)
                .sortBy("title")
                .sortDirection(ListBillsQuery.SortDirection.ASC)
                .build();
        PaginatedResponse<BillSummaryResponse> response = queryHandler.handle(query);

        // Then
        assertEquals(3, response.getContent().size());
        assertEquals("Middle Bill", response.getContent().get(0).getTitle());
        assertEquals("Newest Bill", response.getContent().get(1).getTitle());
        assertEquals("Oldest Bill", response.getContent().get(2).getTitle());
    }

    @Test
    @Transactional
    @DisplayName("Should include OCR results in response")
    void shouldIncludeOcrResultsInResponse() {
        // Given - create bill with OCR data
        BillProjection billWithOcr = BillProjection.builder()
                .id("bill-ocr")
                .title("Original Title")
                .total(new BigDecimal("100.00"))
                .status(BillStatus.PROCESSED)
                .ocrExtractedText("Extracted text content")
                .ocrExtractedTotal(new BigDecimal("95.50"))
                .ocrExtractedTitle("Extracted Title")
                .ocrConfidence("95.2%")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(1L)
                .build();

        entityManager.persist(billWithOcr);
        entityManager.flush();

        FindBillQuery query = new FindBillQuery("bill-ocr");

        // When
        BillResponse response = queryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals("Extracted Title", response.getTitle()); // Should use OCR-extracted title
        assertEquals(new BigDecimal("95.50"), response.getTotal()); // Should use OCR-extracted total
        assertNotNull(response.getOcrResult());
        assertEquals("Extracted text content", response.getOcrResult().getExtractedText());
        assertEquals("Extracted Title", response.getOcrResult().getExtractedTitle());
        assertEquals("95.2%", response.getOcrResult().getConfidence());
    }

    @Test
    @Transactional
    @DisplayName("Should include file count in summary response")
    void shouldIncludeFileCountInSummaryResponse() {
        // Given - create bill with multiple files
        String billId = "bill-with-files";
        createBillProjection(billId, "Bill with Files", new BigDecimal("200.00"));
        createFileProjection(billId, "file1.pdf", "application/pdf", 1024L);
        createFileProjection(billId, "file2.jpg", "image/jpeg", 2048L);
        createFileProjection(billId, "file3.doc", "application/msword", 512L);

        ListBillsQuery query = ListBillsQuery.builder()
                .page(0)
                .size(10)
                .build();

        // When
        PaginatedResponse<BillSummaryResponse> response = queryHandler.handle(query);

        // Then
        assertEquals(1, response.getContent().size());
        BillSummaryResponse summary = response.getContent().get(0);
        assertEquals(billId, summary.getBillId());
        assertTrue(summary.getHasAttachments());
    }

    /**
     * Helper method to create a BillProjection for testing.
     */
    private void createBillProjection(String billId, String title, BigDecimal total) {
        createBillProjectionWithTimestamp(billId, title, total, Instant.now());
    }

    /**
     * Helper method to create a BillProjection with specific timestamp.
     */
    private void createBillProjectionWithTimestamp(String billId, String title, BigDecimal total, Instant timestamp) {
        BillProjection projection = BillProjection.builder()
                .id(billId)
                .title(title)
                .total(total)
                .status(BillStatus.CREATED)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .version(0L)
                .build();

        entityManager.persist(projection);
        entityManager.flush();
    }

    /**
     * Helper method to create a BillProjection with specific status.
     */
    private void createBillProjectionWithStatus(String billId, String title, BigDecimal total, BillStatus status) {
        BillProjection projection = BillProjection.builder()
                .id(billId)
                .title(title)
                .total(total)
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        entityManager.persist(projection);
        entityManager.flush();
    }

    /**
     * Helper method to create a BillFileProjection for testing.
     */
    private String createFileProjection(String billId, String filename, String contentType, Long fileSize) {
        BillFileProjection fileProjection = BillFileProjection.builder()
                .id("file-" + UUID.randomUUID().toString())
                .billId(billId)
                .filename(filename)
                .contentType(contentType)
                .fileSize(fileSize)
                .storagePath("/bills/" + billId + "/" + filename)
                .checksum("checksum-" + filename)
                .attachedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        entityManager.persist(fileProjection);
        entityManager.flush();
        return fileProjection.getId();
    }
}
package com.acme.billing.performance;

import com.acme.billing.BillingApplication;
import com.acme.billing.domain.BillStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Performance tests for the billing system.
 * Tests the performance of read and write operations under various load conditions.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = BillingApplication.class
)
@AutoConfigureWebMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Performance Tests")
class PerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("billing_performance_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    @DisplayName("Should handle concurrent bill creation within performance limits")
    void shouldHandleConcurrentBillCreation() throws Exception {
        int numberOfConcurrentRequests = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(20);

        List<CompletableFuture<String>> futures = new ArrayList<>();
        Instant startTime = Instant.now();

        // Submit concurrent bill creation requests
        for (int i = 0; i < numberOfConcurrentRequests; i++) {
            final int index = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String createBillRequest = String.format("""
                        {
                            "title": "Performance Test Bill %d",
                            "total": %s,
                            "metadata": {
                                "batch": "performance-test",
                                "index": %d
                            }
                        }
                        """, index, 100.00 + (index % 10), index);

                    return mockMvc.perform(post("/api/commands/bills")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBillRequest))
                            .andExpect(status().isAccepted())
                            .andExpect(jsonPath("$.billId").exists())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all requests to complete
        List<String> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);

        // Verify performance and results
        assertEquals(numberOfConcurrentRequests, results.size(),
            "All concurrent requests should complete successfully");

        assertTrue(totalTime.getSeconds() < 30,
            "Concurrent operations should complete within 30 seconds, but took: " + totalTime.getSeconds() + "s");

        // Calculate average time per request
        double avgTimePerRequest = (double) totalTime.toMillis() / numberOfConcurrentRequests;
        assertTrue(avgTimePerRequest < 1000,
            "Average time per request should be less than 1 second, but was: " + avgTimePerRequest + "ms");

        // Verify all bills can be queried (allow time for projections)
        await().atMost(15, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                mockMvc.perform(get("/api/queries/bills")
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(numberOfConcurrentRequests)));
            });

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should maintain query performance with large dataset")
    void shouldMaintainQueryPerformanceWithLargeDataset() throws Exception {
        // Create a large dataset first
        int datasetSize = 100;
        List<String> billIds = new ArrayList<>();

        for (int i = 0; i < datasetSize; i++) {
            String createBillRequest = String.format("""
                {
                    "title": "Large Dataset Bill %d",
                    "total": %s,
                    "metadata": {
                        "batch": "large-dataset",
                        "category": %s
                    }
                }
                """, i, 50.00 + (i % 5), i % 3 == 0 ? "\"utilities\"" : "\"office\"");

            MvcResult result = mockMvc.perform(post("/api/commands/bills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBillRequest))
                    .andExpect(status().isAccepted())
                    .andReturn();

            String billId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("billId").asText();
            billIds.add(billId);
        }

        // Wait for projections
        await().atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                mockMvc.perform(get("/api/queries/bills")
                    .param("page", "0")
                    .param("size", "200")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(datasetSize)));
            });

        // Test query performance with various scenarios
        testQueryPerformance("all bills", "/api/queries/bills", 500); // 500ms
        testQueryPerformance("bills by page", "/api/queries/bills?page=0&size=20", 300); // 300ms
        testQueryPerformance("bills filtered by category", "/api/queries/bills?metadata.category=utilities", 400); // 400ms
        testQueryPerformance("bills filtered by total range", "/api/queries/bills?totalMin=50&totalMax=55", 400); // 400ms
    }

    private void testQueryPerformance(String testName, String endpoint, long maxTimeMs) throws Exception {
        Instant startTime = Instant.now();

        mockMvc.perform(get(endpoint)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Instant endTime = Instant.now();
        Duration queryTime = Duration.between(startTime, endTime);

        assertTrue(queryTime.toMillis() < maxTimeMs,
            String.format("%s query should complete within %dms, but took %dms",
                testName, maxTimeMs, queryTime.toMillis()));
    }

    @Test
    @DisplayName("Should handle file upload operations efficiently")
    void shouldHandleFileUploadOperationsEfficiently() throws Exception {
        // Create bills first
        int numberOfBills = 20;
        List<String> billIds = new ArrayList<>();

        for (int i = 0; i < numberOfBills; i++) {
            String createBillRequest = String.format("""
                {
                    "title": "File Upload Test Bill %d",
                    "total": 75.50
                }
                """, i);

            MvcResult result = mockMvc.perform(post("/api/commands/bills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBillRequest))
                    .andExpect(status().isAccepted())
                    .andReturn();

            String billId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("billId").asText();
            billIds.add(billId);
        }

        // Test concurrent file uploads
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Instant startTime = Instant.now();

        for (String billId : billIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "test-bill.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        ("Test PDF content for bill " + billId).getBytes()
                    );

                    mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId)
                            .file(file))
                        .andExpect(status().isAccepted());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all uploads to complete
        futures.forEach(CompletableFuture::join);
        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);

        double avgTimePerUpload = (double) totalTime.toMillis() / numberOfBills;
        assertTrue(avgTimePerUpload < 2000,
            "Average time per file upload should be less than 2 seconds, but was: " + avgTimePerUpload + "ms");

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should validate pagination performance")
    void shouldValidatePaginationPerformance() throws Exception {
        // Create data with varying totals for pagination testing
        int totalBills = 50;
        for (int i = 0; i < totalBills; i++) {
            String createBillRequest = String.format("""
                {
                    "title": "Pagination Test Bill %d",
                    "total": %s
                }
                """, i, 100.00 + i);

            mockMvc.perform(post("/api/commands/bills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBillRequest))
                    .andExpect(status().isAccepted());
        }

        // Wait for projections
        await().atMost(15, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                mockMvc.perform(get("/api/queries/bills")
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(totalBills)));
            });

        // Test different page sizes
        int[] pageSizes = {5, 10, 20, 25};

        for (int pageSize : pageSizes) {
            int totalPages = (int) Math.ceil((double) totalBills / pageSize);

            for (int page = 0; page < Math.min(totalPages, 3); page++) { // Test first few pages
                Instant startTime = Instant.now();

                mockMvc.perform(get("/api/queries/bills")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(pageSize))
                        .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(pageSize))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(
                        page == totalPages - 1 ? (totalBills % pageSize != 0 ? totalBills % pageSize : pageSize) : pageSize));

                Instant endTime = Instant.now();
                Duration queryTime = Duration.between(startTime, endTime);

                assertTrue(queryTime.toMillis() < 300,
                    String.format("Page %d with size %d should load within 300ms, but took %dms",
                        page, pageSize, queryTime.toMillis()));
            }
        }
    }

    @Test
    @DisplayName("Should handle high volume of read operations")
    void shouldHandleHighVolumeOfReadOperations() throws Exception {
        // Create some test data
        String billId = createTestBill("High Volume Test Bill", new BigDecimal("150.00"));

        // Wait for projection
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> mockMvc.perform(get("/api/queries/bills/{billId}", billId))
                .andExpect(status().isOk()));

        // Test high volume of read operations
        int numberOfReads = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfReads; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Instant startTime = Instant.now();

                    mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                            .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.billId").value(billId));

                    Instant endTime = Instant.now();
                    return Duration.between(startTime, endTime).toMillis();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService);

            futures.add(future);
        }

        List<Long> readTimes = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        // Calculate statistics
        double avgReadTime = readTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxReadTime = readTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minReadTime = readTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        // Performance assertions
        assertTrue(avgReadTime < 100,
            "Average read time should be less than 100ms, but was: " + avgReadTime + "ms");
        assertTrue(maxReadTime < 500,
            "Maximum read time should be less than 500ms, but was: " + maxReadTime + "ms");

        System.out.printf("Read Performance Stats: Avg=%.2fms, Min=%dms, Max=%dms%n",
            avgReadTime, minReadTime, maxReadTime);

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
    }

    private String createTestBill(String title, BigDecimal total) throws Exception {
        String createBillRequest = String.format("""
            {
                "title": "%s",
                "total": %s
            }
            """, title, total);

        MvcResult result = mockMvc.perform(post("/api/commands/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBillRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("billId").asText();
    }
}
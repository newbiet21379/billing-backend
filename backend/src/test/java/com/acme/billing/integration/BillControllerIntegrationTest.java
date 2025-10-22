package com.acme.billing.integration;

import com.acme.billing.BillingApplication;
import com.acme.billing.domain.BillAggregate;
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
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete bill API endpoints.
 * Tests the full request/response cycles including database operations,
 * file storage, and Axon framework integration.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = BillingApplication.class
)
@AutoConfigureWebMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Bill Controller Integration Tests")
class BillControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("billing_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @Container
    static MinIOContainer minio = new MinIOContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database properties
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // MinIO properties
        registry.add("minio.endpoint", minio::getHttpURL);
        registry.add("minio.access-key", minio::getUserName);
        registry.add("minio.secret-key", minio::getPassword);
    }

    @Test
    @DisplayName("Should complete full bill lifecycle: create -> attach file -> approve")
    void shouldCompleteFullBillLifecycle() throws Exception {
        // 1. Create a bill
        String createBillRequest = """
                {
                    "title": "Integration Test Bill",
                    "total": 250.75,
                    "metadata": {
                        "category": "utilities",
                        "department": "engineering"
                    }
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBillRequest))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").exists())
                .andExpect(jsonPath("$.message").value("Bill creation accepted"))
                .andReturn();

        String billId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("billId").asText();

        // 2. Verify bill can be queried (allow time for projection)
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.billId").value(billId))
                            .andExpect(jsonPath("$.title").value("Integration Test Bill"))
                            .andExpect(jsonPath("$.total").value(250.75))
                            .andExpect(jsonPath("$.status").value("CREATED"));
                });

        // 3. Attach a file to the bill
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-bill.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Test PDF content for integration testing".getBytes()
        );

        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId)
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.filename").value("test-bill.pdf"))
                .andExpect(jsonPath("$.message").value("File upload accepted and OCR processing initiated"));

        // 4. Verify bill status updated (allow time for file processing)
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value(not("CREATED")));
                });

        // 5. Approve the bill
        String approveBillRequest = """
                {
                    "approvedBy": "integration-test@example.com",
                    "approvalReason": "Bill approved for integration testing"
                }
                """;

        mockMvc.perform(post("/api/commands/bills/{billId}/approve", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBillRequest))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.approvedBy").value("integration-test@example.com"))
                .andExpect(jsonPath("$.message").value("Bill approval accepted"));

        // 6. Verify bill is approved (allow time for projection update)
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    mockMvc.perform(get("/api/queries/bills/{billId}", billId)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("APPROVED"))
                            .andExpect(jsonPath("$.approvedBy").value("integration-test@example.com"))
                            .andExpect(jsonPath("$.approvalReason").value("Bill approved for integration testing"));
                });
    }

    @Test
    @DisplayName("Should create bill without providing bill ID")
    void shouldCreateBillWithoutProvidingBillId() throws Exception {
        // Given
        String createBillRequest = """
                {
                    "title": "Auto-Generated ID Bill",
                    "total": 100.00
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBillRequest))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").exists())
                .andExpect(jsonPath("$.billId").not(value(blankOrNullString())))
                .andExpect(jsonPath("$.message").value("Bill creation accepted"));
    }

    @Test
    @DisplayName("Should approve bill with default values when no request body provided")
    void shouldApproveBillWithDefaultValuesWhenNoRequestBodyProvided() throws Exception {
        // 1. Create a bill
        String createBillRequest = """
                {
                    "title": "Default Approval Test Bill",
                    "total": 75.50
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBillRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        String billId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("billId").asText();

        // 2. Approve the bill without request body
        mockMvc.perform(post("/api/commands/bills/{billId}/approve", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.approvedBy").value("system"))
                .andExpect(jsonPath("$.message").value("Bill approval accepted"));
    }

    @Test
    @DisplayName("Should list bills with pagination and filtering")
    void shouldListBillsWithPaginationAndFiltering() throws Exception {
        // 1. Create multiple bills with different statuses
        createTestBill("Bill 1", new BigDecimal("100.00"));
        createTestBill("Bill 2", new BigDecimal("200.00"));
        createTestBill("Bill 3", new BigDecimal("300.00"));

        // Wait for projections
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    mockMvc.perform(get("/api/queries/bills")
                                    .param("page", "0")
                                    .param("size", "5")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.content").isArray())
                            .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(3)))
                            .andExpect(jsonPath("$.page").value(0))
                            .andExpect(jsonPath("$.size").value(5))
                            .andExpect(jsonPath("$.totalElements").greaterThanOrEqualTo(3))
                            .andExpect(jsonPath("$.first").value(true));
                });

        // 2. Test filtering by total amount
        mockMvc.perform(get("/api/queries/bills")
                        .param("totalMin", "150")
                        .param("totalMax", "250")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)));

        // 3. Test pagination
        mockMvc.perform(get("/api/queries/bills")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    @DisplayName("Should return 404 for non-existent bill")
    void shouldReturnNotFoundForNonExistentBill() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/queries/bills/{billId}", nonExistentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Should return 400 for invalid bill creation requests")
    void shouldReturnBadRequestForInvalidBillCreationRequests() throws Exception {
        // Test with missing title
        String invalidRequest1 = """
                {
                    "total": 100.00
                }
                """;

        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest1))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        // Test with zero total
        String invalidRequest2 = """
                {
                    "title": "Invalid Bill",
                    "total": 0.00
                }
                """;

        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest2))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        // Test with malformed JSON
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Should return 400 for invalid file uploads")
    void shouldReturnBadRequestForInvalidFileUploads() throws Exception {
        // Create a bill first
        MvcResult createResult = mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "File Test Bill",
                                    "total": 50.00
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        String billId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("billId").asText();

        // Test with empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId)
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        // Test with no file
        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should list bills by status")
    void shouldListBillsByStatus() throws Exception {
        // Create bills with different statuses by simulating the lifecycle
        String bill1Id = createTestBill("Status Test Bill 1", new BigDecimal("100.00"));
        String bill2Id = createTestBill("Status Test Bill 2", new BigDecimal("200.00"));

        // Wait for bills to be created
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    mockMvc.perform(get("/api/queries/bills/by-status/{status}", BillStatus.CREATED)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.content").isArray())
                            .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(2)));
                });

        // Test filtering by CREATED status
        mockMvc.perform(get("/api/queries/bills/by-status/{status}", BillStatus.CREATED)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.status == 'CREATED')]", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("Should list bills pending approval")
    void shouldListBillsPendingApproval() throws Exception {
        // Create some test bills
        createTestBill("Approval Test Bill 1", new BigDecimal("150.00"));
        createTestBill("Approval Test Bill 2", new BigDecimal("250.00"));

        // Test pending approval endpoint
        mockMvc.perform(get("/api/queries/bills/pending-approval")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Should handle validation errors correctly")
    void shouldHandleValidationErrorsCorrectly() throws Exception {
        // Test invalid bill ID format
        String invalidBillId = "invalid-id-that-is-way-too-long-to-be-valid-and-should-fail-validation";

        mockMvc.perform(get("/api/queries/bills/{billId}", invalidBillId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Constraint validation failed"))
                .andExpect(jsonPath("$.invalidParams").exists());

        // Test invalid pagination parameters
        mockMvc.perform(get("/api/queries/bills")
                        .param("page", "-1")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // Helper methods

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
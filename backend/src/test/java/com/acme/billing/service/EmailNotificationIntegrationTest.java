package com.acme.billing.service;

import com.acme.billing.testdata.BillTestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MailHogContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for email notifications with MailHog container.
 * These tests verify that emails are actually sent and received properly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Email Notification Integration Tests")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class EmailNotificationIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // MailHog container for email testing
    @Container
    static GenericContainer<?> mailHog = new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:latest"))
            .withExposedPorts(1025, 8025)
            .withStartupTimeout(Duration.ofMinutes(2));

    // Configure dynamic properties for Spring Mail to use MailHog
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> mailHog.getHost());
        registry.add("spring.mail.port", () -> mailHog.getMappedPort(1025).toString());
        registry.add("billing.notifications.from-address", () -> "test@billing.local");
        registry.add("billing.notifications.admin-email", () -> "admin@billing.local");
    }

    private String mailHogApiUrl;

    @BeforeEach
    void setUp() {
        // Build MailHog API URL for retrieving emails
        int mailHogWebPort = mailHog.getMappedPort(8025);
        mailHogApiUrl = String.format("http://localhost:%d/api/v2", mailHogWebPort);

        // Clear any existing emails
        clearAllEmails();
    }

    @Test
    @DisplayName("Should send and receive OCR completion notification email")
    void shouldSendAndReceiveOcrCompletionNotificationEmail() {
        // Given
        var event = BillTestDataFactory.ocrCompletedEvent();

        // When
        notificationService.sendOcrCompletionNotification(event);

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");
                    Map<String, Object> headers = (Map<String, Object>) content.get("Headers");

                    // Verify email headers
                    assertThat(headers.get("To")).isEqualTo(List.of("admin@billing.local"));
                    assertThat(headers.get("From")).isEqualTo(List.of("test@billing.local"));
                    assertThat(headers.get("Subject")).contains("OCR Processing Completed");
                    assertThat(headers.get("Content-Type")).isNotNull();

                    // Verify email body contains expected content
                    String body = content.get("Body").toString();
                    assertThat(body).contains(event.getBillId());
                    assertThat(body).contains("OCR Processing Complete");
                    assertThat(body).contains("Extracted Information");
                });
    }

    @Test
    @DisplayName("Should send and receive bill approval notification email")
    void shouldSendAndReceiveBillApprovalNotificationEmail() {
        // Given
        var event = BillTestDataFactory.billApprovedEvent();

        // When
        notificationService.sendBillApprovalNotification(event);

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");
                    Map<String, Object> headers = (Map<String, Object>) content.get("Headers");

                    // Verify email headers
                    assertThat(headers.get("To")).isEqualTo(List.of("admin@billing.local"));
                    assertThat(headers.get("From")).isEqualTo(List.of("test@billing.local"));
                    assertThat(headers.get("Subject")).contains("Bill Approved");

                    // Verify email body contains expected content
                    String body = content.get("Body").toString();
                    assertThat(body).contains(event.getBillId());
                    assertThat(body).contains("Bill Decision");
                    assertThat(body).contains("Decision Details");
                    assertThat(body).contains("approved");
                });
    }

    @Test
    @DisplayName("Should send and receive bill rejection notification email")
    void shouldSendAndReceiveBillRejectionNotificationEmail() {
        // Given
        var event = BillTestDataFactory.billRejectedEvent();

        // When
        notificationService.sendBillApprovalNotification(event);

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");

                    // Verify email body contains rejection content
                    String body = content.get("Body").toString();
                    assertThat(body).contains(event.getBillId());
                    assertThat(body).contains("Bill Decision");
                    assertThat(body).contains("rejected");
                    assertThat(body).contains("Amount mismatch detected");
                });
    }

    @Test
    @DisplayName("Should send simple email successfully")
    void shouldSendSimpleEmailSuccessfully() {
        // Given
        String to = "test@example.com";
        String subject = "Test Simple Email";
        String content = "This is a test email content";

        // When
        notificationService.sendSimpleEmail(to, subject, content);

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");
                    Map<String, Object> headers = (Map<String, Object>) content.get("Headers");

                    // Verify email headers
                    assertThat(headers.get("To")).isEqualTo(List.of(to));
                    assertThat(headers.get("From")).isEqualTo(List.of("test@billing.local"));
                    assertThat(headers.get("Subject")).isEqualTo(List.of(subject));

                    // Verify email body
                    String body = content.get("Body").toString();
                    assertThat(body).contains(content);
                });
    }

    @Test
    @DisplayName("Should send multiple emails in sequence")
    void shouldSendMultipleEmailsInSequence() {
        // Given
        var ocrEvent = BillTestDataFactory.ocrCompletedEvent();
        var approvalEvent = BillTestDataFactory.billApprovedEvent();

        // When
        notificationService.sendOcrCompletionNotification(ocrEvent);
        notificationService.sendBillApprovalNotification(approvalEvent);

        // Then
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(2);

                    // Verify both emails are present
                    String email1Subject = getEmailSubject(emails.get(0));
                    String email2Subject = getEmailSubject(emails.get(1));

                    assertThat(email1Subject).containsAnyOf("OCR Processing Completed", "Bill Approved");
                    assertThat(email2Subject).containsAnyOf("OCR Processing Completed", "Bill Approved");
                    assertThat(email1Subject).isNotEqualTo(email2Subject);
                });
    }

    @Test
    @DisplayName("Should handle email with null values gracefully")
    void shouldHandleEmailWithNullValuesGracefully() {
        // Given
        var event = new com.acme.billing.domain.events.OcrCompletedEvent(
                "null-test-bill", null, null, null, null, null, null
        );

        // When
        notificationService.sendOcrCompletionNotification(event);

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");

                    // Verify email body handles null values
                    String body = content.get("Body").toString();
                    assertThat(body).contains("null-test-bill");
                    assertThat(body).contains("Not detected"); // for null total/title
                });
    }

    @Test
    @DisplayName("Should preserve email format and styling")
    void shouldPreserveEmailFormatAndStyling() {
        // Given
        var event = BillTestDataFactory.ocrCompletedEvent();

        // When
        notificationService.sendOcrCompletionNotification(event);

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");

                    String body = content.get("Body").toString();

                    // Verify HTML structure and styling is preserved
                    assertThat(body).contains("<!DOCTYPE html>");
                    assertThat(body).contains("<html");
                    assertThat(body).contains("Billing System");
                    assertThat(body).contains("class=\"container\"");
                    assertThat(body).contains("class=\"header\"");
                    assertThat(body).contains("class=\"highlight success\"");
                    assertThat(body).contains("class=\"section-title\"");
                });
    }

    @Test
    @DisplayName("Should clear emails between tests")
    void shouldClearEmailsBetweenTests() {
        // Given - Send an email in the first test
        notificationService.sendSimpleEmail("test1@example.com", "Test 1", "Content 1");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);
                });

        // When - Clear emails and send another one
        clearAllEmails();
        notificationService.sendSimpleEmail("test2@example.com", "Test 2", "Content 2");

        // Then - Only the second email should be present
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Map<String, Object>> emails = retrieveEmails();
                    assertThat(emails).hasSize(1);

                    Map<String, Object> email = emails.get(0);
                    Map<String, Object> content = (Map<String, Object>) email.get("Content");
                    Map<String, Object> headers = (Map<String, Object>) content.get("Headers");

                    assertThat(headers.get("To")).isEqualTo(List.of("test2@example.com"));
                    assertThat(headers.get("Subject")).isEqualTo(List.of("Test 2"));
                });
    }

    /**
     * Retrieves all emails from MailHog API.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> retrieveEmails() {
        try {
            String url = mailHogApiUrl + "/messages";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody().get("items");
            }
        } catch (Exception e) {
            // Log error but don't fail the test
            System.err.println("Error retrieving emails from MailHog: " + e.getMessage());
        }
        return List.of();
    }

    /**
     * Clears all emails from MailHog.
     */
    private void clearAllEmails() {
        try {
            String url = mailHogApiUrl + "/messages";
            restTemplate.delete(url);
        } catch (Exception e) {
            // Log error but don't fail the test
            System.err.println("Error clearing emails from MailHog: " + e.getMessage());
        }
    }

    /**
     * Extracts subject from email.
     */
    @SuppressWarnings("unchecked")
    private String getEmailSubject(Map<String, Object> email) {
        Map<String, Object> content = (Map<String, Object>) email.get("Content");
        Map<String, Object> headers = (Map<String, Object>) content.get("Headers");
        List<String> subjects = (List<String>) headers.get("Subject");
        return subjects != null && !subjects.isEmpty() ? subjects.get(0) : "";
    }
}
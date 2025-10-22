package com.acme.billing.service;

import com.acme.billing.domain.events.BillApprovedEvent;
import com.acme.billing.domain.events.OcrCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for sending email notifications for billing events.
 * Supports both simple text and HTML email templates using Thymeleaf.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${billing.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${billing.notifications.from-address:noreply@billing.local}")
    private String fromAddress;

    @Value("${billing.notifications.admin-email:admin@billing.local}")
    private String adminEmail;

    /**
     * Sends an OCR completion notification to the admin email.
     *
     * @param event the OCR completed event containing processing results
     */
    public void sendOcrCompletionNotification(OcrCompletedEvent event) {
        if (!notificationsEnabled) {
            log.debug("Notifications are disabled, skipping OCR completion email");
            return;
        }

        try {
            Map<String, Object> templateModel = new HashMap<>();
            templateModel.put("billId", event.getBillId());
            templateModel.put("extractedText", truncateText(event.getExtractedText(), 200));
            templateModel.put("extractedTotal", formatCurrency(event.getExtractedTotal()));
            templateModel.put("extractedTitle", event.getExtractedTitle() != null ? event.getExtractedTitle() : "Not detected");
            templateModel.put("confidence", event.getConfidence());
            templateModel.put("processingTime", event.getProcessingTime());
            templateModel.put("completedAt", event.getCompletedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String subject = String.format("OCR Processing Completed - Bill %s", event.getBillId());
            sendHtmlEmail(adminEmail, subject, "ocr-completion", templateModel);

            log.info("OCR completion notification sent for bill: {}", event.getBillId());
        } catch (Exception e) {
            log.error("Failed to send OCR completion notification for bill: {}", event.getBillId(), e);
            throw new NotificationException("Failed to send OCR completion notification", e);
        }
    }

    /**
     * Sends a bill approval decision notification to the admin email.
     *
     * @param event the bill approval event containing decision details
     */
    public void sendBillApprovalNotification(BillApprovedEvent event) {
        if (!notificationsEnabled) {
            log.debug("Notifications are disabled, skipping bill approval email");
            return;
        }

        try {
            Map<String, Object> templateModel = new HashMap<>();
            templateModel.put("billId", event.getBillId());
            templateModel.put("approverId", event.getApproverId());
            templateModel.put("decision", event.getDecision().name());
            templateModel.put("decisionDisplay", event.getDecision().toString().toLowerCase());
            templateModel.put("reason", event.getReason() != null ? event.getReason() : "No reason provided");
            templateModel.put("approvedAt", event.getApprovedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String decisionText = event.getDecision().toString().toLowerCase();
            String subject = String.format("Bill %s - %s", event.getBillId(), decisionText.substring(0, 1).toUpperCase() + decisionText.substring(1));
            sendHtmlEmail(adminEmail, subject, "bill-approval", templateModel);

            log.info("Bill approval notification sent for bill: {} with decision: {}", event.getBillId(), event.getDecision());
        } catch (Exception e) {
            log.error("Failed to send bill approval notification for bill: {}", event.getBillId(), e);
            throw new NotificationException("Failed to send bill approval notification", e);
        }
    }

    /**
     * Sends a simple text email notification.
     *
     * @param to      recipient email address
     * @param subject email subject
     * @param content email body content
     */
    public void sendSimpleEmail(String to, String subject, String content) {
        if (!notificationsEnabled) {
            log.debug("Notifications are disabled, skipping simple email to: {}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.debug("Simple email sent to: {} with subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send simple email to: {} with subject: {}", to, subject, e);
            throw new NotificationException("Failed to send simple email", e);
        }
    }

    /**
     * Sends an HTML email using a Thymeleaf template.
     *
     * @param to            recipient email address
     * @param subject       email subject
     * @param templateName  Thymeleaf template name (without .html extension)
     * @param templateModel data model for template rendering
     */
    private void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> templateModel) {
        try {
            Context context = new Context();
            context.setVariables(templateModel);

            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.debug("HTML email sent to: {} with subject: {} using template: {}", to, subject, templateName);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to: {} with subject: {} using template: {}", to, subject, templateName, e);
            throw new NotificationException("Failed to send HTML email", e);
        }
    }

    /**
     * Utility method to format currency values.
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "Not detected";
        }
        return String.format("$%.2f", amount);
    }

    /**
     * Utility method to truncate text to specified length.
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Custom exception for notification-related errors.
     */
    public static class NotificationException extends RuntimeException {
        public NotificationException(String message) {
            super(message);
        }

        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
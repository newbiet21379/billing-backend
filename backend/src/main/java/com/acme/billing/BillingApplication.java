package com.acme.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the Billing & Expense Processing Service.
 *
 * This service implements CQRS (Command Query Responsibility Segregation) and Event Sourcing
 * patterns using the Axon Framework to manage the full lifecycle of bill ingestion,
 * OCR processing, event-sourced tracking, and user approval workflows.
 *
 * @author ACME Billing Team
 * @version 1.0.0
 */
@SpringBootApplication
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
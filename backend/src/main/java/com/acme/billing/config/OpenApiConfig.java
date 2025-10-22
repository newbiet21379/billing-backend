package com.acme.billing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenAPI documentation.
 * Provides metadata for the API documentation including server information,
 * contact details, and API versioning.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing & Expense Processing API")
                        .description("""
                                A comprehensive REST API for billing and expense processing built with Spring Boot and
                                Axon Framework using CQRS (Command Query Responsibility Segregation) and
                                Event Sourcing patterns.

                                ## Features
                                * **Bill Management**: Create, update, and track bills through their lifecycle
                                * **File Upload**: Upload bill documents with automatic OCR processing
                                * **Approval Workflows**: Manage bill approval processes
                                * **Event Sourcing**: Complete audit trail of all bill operations
                                * **CQRS Architecture**: Optimized read and write operations

                                ## API Usage
                                The API follows RESTful conventions and uses HTTP status codes to indicate
                                operation results. All endpoints return JSON responses with proper error handling
                                following RFC 7807 Problem Details for HTTP APIs.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ACME Billing Team")
                                .email("billing@acme.com")
                                .url("https://acme.com/billing"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Development server"),
                        new Server()
                                .url("https://api.acme.com/billing")
                                .description("Production server")))
                .tags(List.of(
                        new Tag()
                                .name("Bill Commands")
                                .description("Write operations for creating and modifying bills"),
                        new Tag()
                                .name("Bill Queries")
                                .description("Read operations for retrieving bill information"),
                        new Tag()
                                .name("File Operations")
                                .description("File upload and management operations")));
    }
}
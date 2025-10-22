# Billing Backend Service

This is the main Spring Boot application that implements CQRS and Event Sourcing patterns using the Axon Framework.

## Architecture

- **Command Side**: Handles write operations and publishes domain events
- **Query Side**: Projects read models from events using Spring Data JPA
- **Event Store**: Axon Server stores and routes events
- **Storage**: MinIO for bill image storage
- **OCR**: External FastAPI service for text extraction

## Key Features

- Event-sourced bill management
- OCR processing workflow
- File attachment handling
- Email notifications
- REST APIs for commands and queries
- OpenAPI/Swagger documentation

## Configuration

The application is configured via `application.yml` with profiles for different environments.

## Running

```bash
# Development
mvn spring-boot:run

# Production
java -jar target/billing-backend-1.0.0-SNAPSHOT.jar
```
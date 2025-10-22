Perfect — here’s a **`CLAUDE.md`** file (planning + task roadmap) that documents your backend architecture, components, and next actionable tasks.
You can drop this into your repo root (`billing-backend/CLAUDE.md`).

---

# `CLAUDE.md`

## 🧭 Overview

**Project:** Billing & Expense Processing Service
**Stack:** Spring Boot (Java 21) • Axon (CQRS + Event Sourcing) • Postgres • MinIO • OCR (FastAPI + Tesseract) • MailHog (SMTP testing)
**Runtime:** Docker Compose

This service manages the full lifecycle of **bill ingestion, OCR processing, event-sourced tracking, and user approval workflows**, using **CQRS** and **Event Sourcing** patterns to ensure traceability and scalability.

---

## ⚙️ Architecture Summary

| Layer                    | Technology                   | Purpose                                           |
| ------------------------ | ---------------------------- | ------------------------------------------------- |
| **Command Side**         | Spring Boot + Axon Framework | Handles write operations, publishes domain events |
| **Event Store / Bus**    | Axon Server                  | Stores and routes domain events                   |
| **Query Side**           | Spring Data JPA (PostgreSQL) | Projects read models from events                  |
| **Storage**              | MinIO (S3-compatible)        | Stores uploaded bill images                       |
| **OCR Service**          | FastAPI + Tesseract OCR      | Extracts text data from uploaded bill images      |
| **Mail / Notifications** | Spring Mail + MailHog        | Sends event-based notifications                   |
| **Deployment**           | Docker Compose               | Runs all services locally and in CI environments  |

---

## 🧩 Key Services

### 1. Backend Service (Spring Boot)

* Implements CQRS & ES via Axon
* Command & Query REST APIs:

  * `/api/commands/bills`
  * `/api/queries/bills`
* Stores events in Axon Server
* Projects read models into PostgreSQL
* Triggers OCR via event handlers
* Sends notifications via SMTP
* Provides S3 upload/presign utilities for MinIO

### 2. OCR Service (FastAPI + Tesseract)

* REST endpoint `/ocr` for reading bills
* Returns extracted text + structured fields (Total, Title, etc.)
* Lightweight and stateless — ready for scaling

### 3. Axon Server

* Event store + routing for commands, events, and queries
* Developer dashboard available at `http://localhost:8024`

### 4. PostgreSQL

* Stores query-side data (projections)
* Auto-created tables via Spring Data JPA

### 5. MinIO

* Object storage for uploaded bills
* Accessible at `http://localhost:9001`

### 6. MailHog

* Local SMTP testing
* UI: `http://localhost:8025`

---

## 🧱 Folder Structure

```
billing-backend/
├─ backend/                  # Spring Boot CQRS + ES app
│  ├─ src/main/java/com/acme/billing/
│  │  ├─ api/                # Commands / Queries DTOs
│  │  ├─ domain/             # Aggregates + Events
│  │  ├─ projection/         # Query-side projections
│  │  ├─ service/            # OCR client, S3 service
│  │  ├─ web/                # REST controllers
│  │  └─ util/               # Misc helpers
│  └─ src/main/resources/
│     └─ application.yml
│
├─ ocr-service/              # FastAPI + Tesseract OCR service
│  ├─ app.py
│  └─ Dockerfile
│
├─ docker-compose.yml        # Environment setup
├─ CLAUDE.md                 # This planning file
└─ README.md                 # Developer usage guide
```

---

## 🪜 Setup Flow

```bash
# 1. Build all containers
docker compose build

# 2. Start the system
docker compose up

# 3. Access services
# Backend API:  http://localhost:8080
# OCR API:      http://localhost:7070
# MailHog UI:   http://localhost:8025
# MinIO Console: http://localhost:9001
# Axon Dashboard: http://localhost:8024
```

---

## 🔄 Typical Flow

1. **Upload bill →** `/api/commands/bills` creates bill aggregate
2. **Attach file →** triggers `FileAttachedEvent` + `OcrRequestedEvent`
3. **OCR Service →** extracts data and returns text
4. **ApplyOcrResultCommand →** updates read model
5. **Email Notification →** sent when OCR completes
6. **Approve bill →** `/api/commands/bills/{id}/approve` updates status
7. **Query →** `/api/queries/bills` lists bills with OCR & approval data

---

## 🧠 CQRS + Event Sourcing Highlights

* **Write model (Aggregate):** Processes `Command` and emits domain `Event`
* **Event Store:** Axon Server persists immutable events
* **Read model:** PostgreSQL projection reacts to events
* **Event replay:** Projections can be rebuilt anytime
* **Loose coupling:** OCR, mail, and projection layers react independently

---

## 📦 Docker Compose Summary

| Service      | Port      | Description           |
| ------------ | --------- | --------------------- |
| `backend`    | 8080      | Main CQRS backend     |
| `ocr`        | 7070      | OCR microservice      |
| `axonserver` | 8024/8124 | Axon dashboard + gRPC |
| `postgres`   | 5432      | Query-side DB         |
| `minio`      | 9000/9001 | Object storage        |
| `mailhog`    | 1025/8025 | SMTP testing          |

---

## 🚧 TODO / Next Tasks

### 🧰 Core

* [ ] ✅ Implement event-sourced BillAggregate and projection
* [ ] 🔲 Add OpenAPI/Swagger via SpringDoc (`springdoc-openapi-starter-webmvc-ui`)
* [ ] 🔲 Add structured error handling (RFC 7807-style)
* [ ] 🔲 Add command validation and Axon command interceptors
* [ ] 🔲 Add `BillDeleted` and `BillUpdated` commands/events

### 🧾 OCR & File Handling

* [ ] 🔲 Add retry logic & circuit breaker for OCR (Resilience4j)
* [ ] 🔲 Support multiple image uploads per bill
* [ ] 🔲 Cache OCR results in query DB
* [ ] 🔲 Add asynchronous job queue (Axon subscription or Kafka bridge)

### 📧 Notifications

* [ ] 🔲 Add configurable mail templates
* [ ] 🔲 Add notification on approval
* [ ] 🔲 Replace MailHog with real SMTP (Gmail / SES) in production

### 🔒 Security

* [ ] 🔲 Integrate JWT auth (Spring Security + Keycloak)
* [ ] 🔲 Role-based access for commands (creator vs approver)
* [ ] 🔲 Add audit trail events for sensitive operations

### 📊 Observability

* [ ] 🔲 Add Prometheus + Grafana for metrics
* [ ] 🔲 Add Zipkin or OpenTelemetry tracing
* [ ] 🔲 Add Axon event metrics

### 🧪 Testing

* [ ] 🔲 Add unit tests for commands and aggregates (`AxonTestFixture`)
* [ ] 🔲 Integration tests for event flow (Testcontainers)
* [ ] 🔲 OCR service test images

### 🚀 DevOps

* [ ] 🔲 Add CI/CD (GitHub Actions)
* [ ] 🔲 Add `wait-for-it` entrypoint for container dependency checks
* [ ] 🔲 Prepare Helm chart for Kubernetes
* [ ] 🔲 Add `.env` file for secrets/variables

### 📘 Documentation

* [ ] 🔲 Add detailed README with API usage examples
* [ ] 🔲 Add Postman collection for API testing
* [ ] 🔲 Add sequence diagrams for CQRS event flow

---

## 🧩 Future Extensions

| Feature                     | Description                                   |
| --------------------------- | --------------------------------------------- |
| **Payment Integration**     | Add Stripe/PayPal for bill settlement         |
| **Multi-Tenant Support**    | Separate Axon context per customer            |
| **AI Line Item Extraction** | Enhance OCR parsing using LLM or GPT-4 Vision |
| **User Portal (Next.js)**   | Add front-end dashboard for managing bills    |
| **Attachment Virus Scan**   | Integrate ClamAV before storing in MinIO      |

---

## 🧠 Notes

* Axon Server and PostgreSQL must both be ready before backend starts.
  Use `depends_on` in Docker Compose or a startup script.
* OCR processing is event-driven — no blocking calls on upload.
* MailHog is only for local testing. Replace with production SMTP later.
* Each service runs independently; Docker Compose ensures network connectivity via internal DNS.

---

## ✅ Quick Health Check

| Endpoint                                | Description                |
| --------------------------------------- | -------------------------- |
| `GET /actuator/health`                  | Spring Boot health         |
| `GET /api/queries/bills`                | List bills                 |
| `GET /api/queries/bills/{id}`           | Get bill details           |
| `POST /api/commands/bills`              | Create bill                |
| `POST /api/commands/bills/{id}/file`    | Upload file (triggers OCR) |
| `POST /api/commands/bills/{id}/approve` | Approve bill               |

---

Would you like me to make this **`CLAUDE.md`** auto-generate from your project folder (like a `Makefile doc` or Gradle task) so it stays updated automatically when you add new components?

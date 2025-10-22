# Docker Compose Usage Guide

## Quick Start

```bash
# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Stop all services
docker compose down

# Stop and remove volumes (data loss!)
docker compose down -v
```

## Service Endpoints

| Service | Port | Description | URL |
|---------|------|-------------|-----|
| Backend API | 8080 | Spring Boot REST API | http://localhost:8080 |
| OCR Service | 7070 | FastAPI OCR Service | http://localhost:7070 |
| Axon Dashboard | 8024 | Axon Server UI | http://localhost:8024 |
| PostgreSQL | 5432 | Database | localhost:5432 |
| MinIO Console | 9001 | Object Storage UI | http://localhost:9001 |
| MailHog UI | 8025 | SMTP Testing | http://localhost:8025 |

## Database Connection

- **Host:** localhost:5432
- **Database:** billing_db
- **Username:** billing_user
- **Password:** billing_password

## MinIO Access

- **Access Key:** minioadmin
- **Secret Key:** minioadmin123
- **Default Bucket:** bills (auto-created by application)

## Health Checks

All services include health checks. Monitor with:

```bash
# Check all service health
docker compose ps

# Check specific service health
docker compose exec backend curl -f http://localhost:8080/actuator/health
```

## Development Tips

1. **Hot Reload:** For development, use volumes to mount source code
2. **Debug Logs:** Set log levels via environment variables
3. **Service Dependencies:** Backend waits for all services to be healthy
4. **Data Persistence:** All data is persisted in named volumes

## Common Issues

- **Port Conflicts:** Ensure ports 5432, 7070, 8080, 8024, 8025, 9000, 9001 are available
- **Permission Issues:** Use `sudo` if needed for Docker operations
- **Network Issues:** All services communicate via `billing-network` bridge network
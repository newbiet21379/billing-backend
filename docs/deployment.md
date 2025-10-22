# Deployment Guide

This comprehensive guide covers deployment strategies, configurations, and best practices for deploying the Billing & Expense Processing Service to various environments.

## Deployment Overview

The Billing system is designed for cloud-native deployment with support for:
- **Docker Compose** - Local development and staging
- **Kubernetes** - Production orchestration
- **AWS/Azure/GCP** - Cloud provider deployments
- **On-premise** - Traditional data center deployments

## Prerequisites

### Infrastructure Requirements

#### Minimum Production Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| **CPU** | 4 vCPU | 8 vCPU |
| **Memory** | 8 GB RAM | 16 GB RAM |
| **Storage** | 100 GB SSD | 500 GB SSD |
| **Network** | 1 Gbps | 10 Gbps |
| **Load Balancer** | Standard | High availability |

#### Software Requirements

- **Docker** 20.10+ and **Docker Compose** 2.0+
- **Kubernetes** 1.24+ (for K8s deployment)
- **PostgreSQL** 15+ (or managed database service)
- **MinIO** 4.0+ or AWS S3-compatible storage
- **Axon Server** 4.6+ (or Axon Cloud)

### Security Requirements

- **SSL/TLS** certificates for HTTPS
- **Firewall** rules for service access
- **Secrets management** for sensitive data
- **Network security** groups/VPC configuration
- **Authentication** system integration

## Environment Configuration

### Environment-Specific Variables

#### Development Environment

```bash
# .env.development
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/billing_dev
SPRING_DATASOURCE_USERNAME=billing_dev
SPRING_DATASOURCE_PASSWORD=dev_password

# Axon Server
AXON_AXONSERVER_SERVERS=localhost:8124

# MinIO (Local)
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=billing-dev

# OCR Service
OCR_SERVICE_URL=http://localhost:7070

# Mail (MailHog)
SPRING_MAIL_HOST=localhost
SPRING_MAIL_PORT=1025

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_ACME_BILLING=DEBUG
```

#### Staging Environment

```bash
# .env.staging
SPRING_PROFILES_ACTIVE=staging
SERVER_PORT=8080

# Database (Managed)
SPRING_DATASOURCE_URL=jdbc:postgresql://staging-db.company.com:5432/billing_staging
SPRING_DATASOURCE_USERNAME=billing_staging
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# Axon Server (Staging)
AXON_AXONSERVER_SERVERS=staging-axon.company.com:8124
AXON_AXONSERVER_TOKEN=${AXON_TOKEN}

# MinIO (Staging)
MINIO_ENDPOINT=https://staging-storage.company.com
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
MINIO_BUCKET_NAME=billing-staging
MINIO_REGION=us-east-1

# OCR Service (Staging)
OCR_SERVICE_URL=https://staging-ocr.company.com
OCR_SERVICE_API_KEY=${OCR_API_KEY}

# Mail (SendGrid)
SPRING_MAIL_HOST=smtp.sendgrid.net
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=${SENDGRID_USERNAME}
SPRING_MAIL_PASSWORD=${SENDGRID_PASSWORD}

# Security
JWT_SECRET=${JWT_SECRET}
CORS_ALLOWED_ORIGINS=https://staging.company.com

# Monitoring
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=true
```

#### Production Environment

```bash
# .env.production
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# Database (Managed Cloud)
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db.company.com:5432/billing_prod
SPRING_DATASOURCE_USERNAME=billing_prod
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=5

# Axon Server (Cluster)
AXON_AXONSERVER_SERVERS=axon-1.company.com:8124,axon-2.company.com:8124,axon-3.company.com:8124
AXON_AXONSERVER_TOKEN=${AXON_TOKEN}
AXON_SERIALIZATION_MESSAGES=JACKSON

# MinIO (S3 Compatible)
MINIO_ENDPOINT=https://storage.company.com
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
MINIO_BUCKET_NAME=billing-prod
MINIO_REGION=us-east-1
MINIO_SECURE=true

# OCR Service (Load Balanced)
OCR_SERVICE_URL=https://ocr.company.com
OCR_SERVICE_API_KEY=${OCR_API_KEY}
OCR_SERVICE_TIMEOUT=30s
OCR_SERVICE_RETRY_ATTEMPTS=3

# Mail (Production SMTP)
SPRING_MAIL_HOST=smtp.company.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=${SMTP_USERNAME}
SPRING_MAIL_PASSWORD=${SMTP_PASSWORD}
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true

# Security
JWT_SECRET=${JWT_SECRET}
JWT_EXPIRATION=3600
CORS_ALLOWED_ORIGINS=https://app.company.com,https://admin.company.com

# Performance
SERVER_TOMCAT_THREADS_MAX=200
SERVER_TOMCAT_ACCEPT_COUNT=100

# Monitoring & Observability
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=true
MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin.company.com:9411
LOGGING_STRUCTURED_JSON=true
```

## Docker Compose Deployment

### Staging Deployment

Create `docker-compose.staging.yml`:

```yaml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: billing-staging-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_staging_data:/var/lib/postgresql/data
      - ./docker/init-postgres.sql:/docker-entrypoint-initdb.d/init.sql:ro
    ports:
      - "5432:5432"
    restart: unless-stopped
    networks:
      - billing-staging-network

  # Axon Server
  axonserver:
    image: axoniq/axonserver:4.6.3
    container_name: billing-staging-axonserver
    environment:
      AXONIQ_AXONSERVER_HOSTNAME: axonserver
      AXONIQ_AXONSERVER_EVENTSTORE_TOKEN: ${AXON_TOKEN}
      AXONIQ_AXONSERVER_CONTROL_TOKEN: ${AXON_TOKEN}
    ports:
      - "8024:8024"
      - "8124:8124"
    volumes:
      - axon_staging_data:/data
      - axon_staging_events:/eventstore
    restart: unless-stopped
    networks:
      - billing-staging-network

  # MinIO Storage
  minio:
    image: minio/minio:latest
    container_name: billing-staging-minio
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_staging_data:/data
    restart: unless-stopped
    networks:
      - billing-staging-network

  # OCR Service
  ocr:
    image: billing-ocr:staging
    container_name: billing-staging-ocr
    environment:
      - OCR_LOG_LEVEL=INFO
      - SERVICE_HOST=0.0.0.0
      - SERVICE_PORT=7070
    ports:
      - "7070:7070"
    restart: unless-stopped
    depends_on:
      - postgres
      - axonserver
    networks:
      - billing-staging-network

  # Backend Application
  backend:
    image: billing-backend:staging
    container_name: billing-staging-backend
    env_file:
      - .env.staging
    ports:
      - "8080:8080"
    volumes:
      - ./logs:/app/logs
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      axonserver:
        condition: service_healthy
      minio:
        condition: service_healthy
      ocr:
        condition: service_healthy
    networks:
      - billing-staging-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Nginx Reverse Proxy
  nginx:
    image: nginx:alpine
    container_name: billing-staging-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.staging.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    restart: unless-stopped
    depends_on:
      - backend
    networks:
      - billing-staging-network

volumes:
  postgres_staging_data:
  axon_staging_data:
  axon_staging_events:
  minio_staging_data:

networks:
  billing-staging-network:
    driver: bridge
```

### Production Deployment

Create `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  # Backend Application (Multi-replica)
  backend:
    image: billing-backend:production
    deploy:
      replicas: 3
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
    env_file:
      - .env.production
    networks:
      - billing-prod-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 3
      start_period: 30s

  # Load Balancer (HAProxy)
  loadbalancer:
    image: haproxy:2.6
    deploy:
      replicas: 2
      restart_policy:
        condition: on-failure
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./haproxy/haproxy.prod.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro
      - ./ssl:/etc/ssl/certs:ro
    depends_on:
      - backend
    networks:
      - billing-prod-network

networks:
  billing-prod-network:
    driver: overlay
    attachable: true
```

### Deployment Commands

```bash
# Deploy to staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d

# Deploy to production
docker compose -f docker-compose.prod.yml --env-file .env.production up -d

# Scale services
docker compose -f docker-compose.prod.yml up -d --scale backend=5

# Update services
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d

# Check deployment status
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

## Kubernetes Deployment

### Namespace and Configuration

Create `k8s/namespace.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: billing
  labels:
    name: billing
    environment: production
```

### ConfigMaps

Create `k8s/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: billing-config
  namespace: billing
data:
  application.yml: |
    spring:
      profiles:
        active: prod
      datasource:
        url: jdbc:postgresql://postgres-service:5432/billing_prod
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
      jpa:
        hibernate:
          ddl-auto: validate
        show-sql: false

    axon:
      axonserver:
        servers: axonserver-service:8124

    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      metrics:
        export:
          prometheus:
            enabled: true

    logging:
      level:
        com.acme.billing: INFO
        root: WARN
      structured: true
```

### Secrets

Create `k8s/secrets.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: billing-secrets
  namespace: billing
type: Opaque
data:
  DB_USERNAME: YmlsbGluZ19wcm9k  # base64 encoded
  DB_PASSWORD: c3Ryb25nX3Bhc3N3b3Jk  # base64 encoded
  JWT_SECRET: c3VwZXJfc2VjcmV0X2p3dF9rZXk=  # base64 encoded
  MINIO_ACCESS_KEY: bWluaW9hZG1pbg==  # base64 encoded
  MINIO_SECRET_KEY: bWluaW9wYXNzd29yZA==  # base64 encoded
  OCR_API_KEY: b2NyX2FwaV9rZXlfMTIz  # base64 encoded
```

### Deployments

#### PostgreSQL Deployment

Create `k8s/postgres-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: billing
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        env:
        - name: POSTGRES_DB
          value: billing_prod
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: DB_USERNAME
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: DB_PASSWORD
        ports:
        - containerPort: 5432
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
      volumes:
      - name: postgres-storage
        persistentVolumeClaim:
          claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-service
  namespace: billing
spec:
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
  type: ClusterIP
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: billing
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 100Gi
  storageClassName: fast-ssd
```

#### Axon Server Deployment

Create `k8s/axonserver-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: axonserver
  namespace: billing
spec:
  replicas: 1
  selector:
    matchLabels:
      app: axonserver
  template:
    metadata:
      labels:
        app: axonserver
    spec:
      containers:
      - name: axonserver
        image: axoniq/axonserver:4.6.3
        env:
        - name: AXONIQ_AXONSERVER_HOSTNAME
          value: axonserver
        - name: AXONIQ_AXONSERVER_EVENTSTORE_TOKEN
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: AXON_TOKEN
        - name: AXONIQ_AXONSERVER_CONTROL_TOKEN
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: AXON_TOKEN
        ports:
        - containerPort: 8024
        - containerPort: 8124
        volumeMounts:
        - name: axon-data
          mountPath: /data
        - name: axon-events
          mountPath: /eventstore
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8024
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8024
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: axon-data
        persistentVolumeClaim:
          claimName: axon-data-pvc
      - name: axon-events
        persistentVolumeClaim:
          claimName: axon-events-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: axonserver-service
  namespace: billing
spec:
  selector:
    app: axonserver
  ports:
  - name: http
    port: 8024
    targetPort: 8024
  - name: grpc
    port: 8124
    targetPort: 8124
  type: ClusterIP
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: axon-data-pvc
  namespace: billing
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 50Gi
  storageClassName: fast-ssd
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: axon-events-pvc
  namespace: billing
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 100Gi
  storageClassName: fast-ssd
```

#### Backend Application Deployment

Create `k8s/backend-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: billing
  labels:
    app: backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: backend
        image: billing-backend:production
        envFrom:
        - configMapRef:
            name: billing-config
        - secretRef:
            name: billing-secrets
        ports:
        - containerPort: 8080
        volumeMounts:
        - name: logs
          mountPath: /app/logs
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
      volumes:
      - name: logs
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: backend-service
  namespace: billing
spec:
  selector:
    app: backend
  ports:
  - port: 8080
    targetPort: 8080
  type: ClusterIP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: billing
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Ingress Configuration

Create `k8s/ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: billing-ingress
  namespace: billing
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
    nginx.ingress.kubernetes.io/enable-cors: "true"
    nginx.ingress.kubernetes.io/cors-allow-origin: "https://app.company.com,https://admin.company.com"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - api.billing.company.com
    secretName: billing-tls
  rules:
  - host: api.billing.company.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: backend-service
            port:
              number: 8080
```

### Deployment Commands

```bash
# Apply all configurations
kubectl apply -f k8s/

# Check deployment status
kubectl get all -n billing
kubectl describe deployment backend -n billing

# Scale deployment
kubectl scale deployment backend --replicas=5 -n billing

# Check logs
kubectl logs deployment/backend -n billing -f

# Check resource usage
kubectl top pods -n billing

# Get shell access
kubectl exec -it deployment/backend -n billing -- /bin/bash
```

## Cloud Provider Deployments

### AWS Deployment

#### ECS Fargate Deployment

```yaml
# task-definition.json
{
  "family": "billing-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::account:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::account:role/ecsTaskRole",
  "containerDefinitions": [
    {
      "name": "billing-backend",
      "image": "account.dkr.ecr.region.amazonaws.com/billing-backend:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "aws"
        }
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:billing/db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/billing-backend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      }
    }
  ]
}
```

#### Terraform Configuration

```hcl
# terraform/main.tf

provider "aws" {
  region = var.aws_region
}

# ECS Cluster
resource "aws_ecs_cluster" "billing" {
  name = "billing-cluster"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# VPC Configuration
resource "aws_vpc" "billing" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "billing-vpc"
  }
}

# Application Load Balancer
resource "aws_lb" "billing" {
  name               = "billing-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = false

  tags = {
    Environment = var.environment
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "billing" {
  identifier     = "billing-db"
  engine         = "postgres"
  engine_version = "15.3"
  instance_class = "db.t3.medium"

  allocated_storage     = 100
  max_allocated_storage = 1000
  storage_encrypted     = true

  db_name  = "billing_prod"
  username = var.db_username
  password = var.db_password

  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.billing.name

  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"

  skip_final_snapshot = false
  final_snapshot_identifier = "billing-db-final-snapshot"

  tags = {
    Environment = var.environment
  }
}

# S3 Bucket for File Storage
resource "aws_s3_bucket" "billing" {
  bucket = var.s3_bucket_name
}

resource "aws_s3_bucket_versioning" "billing" {
  bucket = aws_s3_bucket.billing.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_encryption" "billing" {
  bucket = aws_s3_bucket.billing.id

  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
      }
    }
  }
}
```

### Azure Deployment

#### AKS Deployment

```yaml
# azure/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: billing-backend
  namespace: billing
spec:
  replicas: 3
  selector:
    matchLabels:
      app: billing-backend
  template:
    metadata:
      labels:
        app: billing-backend
    spec:
      containers:
      - name: billing-backend
        image: billingregistry.azurecr.io/billing-backend:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "azure"
        - name: AZURE_STORAGE_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: azure-storage-connection-string
        - name: DB_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: db-connection-string
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

### GCP Deployment

#### GKE Deployment

```yaml
# gcp/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: billing-backend
  namespace: billing
spec:
  replicas: 3
  selector:
    matchLabels:
      app: billing-backend
  template:
    metadata:
      labels:
        app: billing-backend
    spec:
      containers:
      - name: billing-backend
        image: gcr.io/your-project/billing-backend:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "gcp"
        - name: GOOGLE_CLOUD_PROJECT
          value: "your-project"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: db-host
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: billing-secrets
              key: db-password
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
```

## CI/CD Pipeline

### GitHub Actions

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to Production

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    - name: Run unit tests
      run: ./mvnw test
    - name: Run integration tests
      run: ./mvnw verify -P integration-tests
    - name: Generate test report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Maven Tests
        path: target/surefire-reports/*.xml
        reporter: java-junit

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    outputs:
      image: ${{ steps.image.outputs.image }}
      digest: ${{ steps.build.outputs.digest }}
    steps:
    - uses: actions/checkout@v3
    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v2
    - name: Log in to Container Registry
      uses: docker/login-action@v2
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v4
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
    - name: Build and push Docker image
      id: build
      uses: docker/build-push-action@v4
      with:
        context: ./backend
        push: true
        tags: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
        labels: ${{ steps.meta.outputs.labels }}
        cache-from: type=gha
        cache-to: type=gha,mode=max

  deploy-staging:
    needs: build-and-push
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: staging
    steps:
    - uses: actions/checkout@v3
    - name: Deploy to staging
      run: |
        echo "Deploying to staging environment"
        # Add staging deployment commands
        # kubectl apply -f k8s/staging/
        # kubectl set image deployment/staging-backend backend=${{ needs.build-and-push.outputs.image }}:latest

  deploy-production:
    needs: [build-and-push, deploy-staging]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: production
    steps:
    - uses: actions/checkout@v3
    - name: Deploy to production
      run: |
        echo "Deploying to production environment"
        # Add production deployment commands
        # kubectl apply -f k8s/production/
        # kubectl set image deployment/backend backend=${{ needs.build-and-push.outputs.image }}:latest
        # kubectl rollout status deployment/backend -n billing
```

### Monitoring and Alerting

#### Prometheus Configuration

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "billing-rules.yml"

scrape_configs:
  - job_name: 'billing-backend'
    static_configs:
      - targets: ['backend-service:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  - job_name: 'axon-server'
    static_configs:
      - targets: ['axonserver-service:8024']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

#### Alert Rules

```yaml
# monitoring/billing-rules.yml
groups:
- name: billing-alerts
  rules:
  - alert: BackendDown
    expr: up{job="billing-backend"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Billing backend is down"
      description: "Billing backend has been down for more than 1 minute"

  - alert: HighErrorRate
    expr: rate(http_server_requests_total{status=~"5..", job="billing-backend"}[5m]) > 0.1
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "High error rate detected"
      description: "Error rate is {{ $value }} errors per second"

  - alert: HighMemoryUsage
    expr: (jvm_memory_used_bytes{job="billing-backend"} / jvm_memory_max_bytes{job="billing-backend"}) * 100 > 80
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High memory usage"
      description: "Memory usage is above 80%"

  - alert: DatabaseConnectionFailure
    expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Database connection pool exhausted"
      description: "Database connection pool is 90% utilized"
```

## Backup and Disaster Recovery

### Database Backup Strategy

```bash
#!/bin/bash
# scripts/backup-database.sh

DB_HOST="postgres-service"
DB_NAME="billing_prod"
DB_USER="billing_prod"
BACKUP_DIR="/backups/postgres"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/billing_backup_$DATE.sql"

# Create backup directory if it doesn't exist
mkdir -p $BACKUP_DIR

# Perform backup
kubectl exec -n billing deployment/postgres -- pg_dump -U $DB_USER $DB_NAME > $BACKUP_FILE

# Compress backup
gzip $BACKUP_FILE

# Keep only last 7 days of backups
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_FILE.gz"
```

### Axon Server Backup

```bash
#!/bin/bash
# scripts/backup-axon.sh

AXON_DATA_DIR="/data/axon"
BACKUP_DIR="/backups/axon"
DATE=$(date +%Y%m%d_%H%M%S)

# Create backup directory
mkdir -p $BACKUP_DIR

# Backup Axon data
kubectl exec -n billing deployment/axonserver -- tar -czf /tmp/axon_backup_$DATE.tar.gz -C $AXON_DATA_DIR .

# Copy backup to persistent storage
kubectl cp billing/axonserver:/tmp/axon_backup_$DATE.tar.gz $BACKUP_DIR/

echo "Axon backup completed: $BACKUP_DIR/axon_backup_$DATE.tar.gz"
```

This comprehensive deployment guide provides everything needed to deploy the Billing & Expense Processing Service across different environments and cloud platforms with proper monitoring, backup, and disaster recovery strategies.
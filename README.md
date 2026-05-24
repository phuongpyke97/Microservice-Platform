# Mytel CRBT Microservice Platform

Hệ thống microservice cho dịch vụ CRBT (CallerRing Back Tone) với khả năng tạo nhạc chờ AI-powered, quản lý gói cước, ví credit, và tích hợp CMS Mytone.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Java Services** | Spring Boot 3.2.5 · Java 21 · Maven Multi-module |
| **Python Service** | FastAPI · gRPC · Python 3.11 |
| **Database** | PostgreSQL 16 (mỗi service 1 DB riêng) |
| **Cache / Lock** | Redis 7.2 + Redisson |
| **Message Broker** | RabbitMQ 3.12 |
| **Object Storage** | MinIO (S3-compatible) |
| **Service Registry** | Netflix Eureka |
| **Config Center** | Spring Cloud Config Server |
| **API Gateway** | Spring Cloud Gateway |
| **Circuit Breaker** | Resilience4j |
| **Observability** | Prometheus + Grafana + Loki + Promtail |
| **Tracing** | Micrometer Tracing (Brave) |
| **AI Music** | Google Gemini Lyria 3 |
| **TTS** | Microsoft Edge TTS |
| **Audio Processing** | Spleeter / Demucs |

---

## Architecture Overview

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│              API Gateway (8080)                         │
│  - Rate Limiting                                        │
│  - JWT Validation                                       │
│  - X-User-Id Header Injection                          │
└─────────────────────────────────────────────────────────┘
       │
       ├──────────────────────────────────────────────────┐
       │                                                   │
       ▼                                                   ▼
┌──────────────────┐                          ┌──────────────────┐
│ Infra Services   │                          │ Business Services│
│ - Auth (8081)    │                          │ - Campaign (8090)│
│ - Notification   │                          │ - Library (8091) │
│   (8082)         │                          │ - Audio Gen      │
│ - File (8083)    │                          │   (8092)         │
│ - Audit (8084)   │                          │ - Transaction    │
│ - Payment (8085) │                          │   (8093)         │
│ - Wallet (8086)  │                          │ - Core Adapter   │
└──────────────────┘                          │   (8094)         │
       │                                      └──────────────────┘
       │                                               │
       ▼                                               ▼
┌──────────────────────────────────────────────────────────┐
│              Python AI Worker (8765)                     │
│  - Chorus Detection (NumPy SSM)                          │
│  - Audio Separation (Spleeter)                           │
│  - Edge TTS Streaming                                    │
└──────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────┐
│              Data Stores                                 │
│  - PostgreSQL (5432)                                     │
│  - Redis (6379)                                          │
│  - RabbitMQ (5672, 15672)                                │
│  - MinIO (9000, 9001)                                    │
└──────────────────────────────────────────────────────────┘
```

---

## Port Map

| Service | Port | Layer | Note |
|---|---|---|---|
| **api-gateway** | 8080 | Infrastructure | Cổng duy nhất từ Client |
| **auth-service** | 8081 | Infra-Services | JWT, BCrypt strength 12 |
| **notification-service** | 8082 | Infra-Services | RabbitMQ consumer only |
| **file-service** | 8083 | Infra-Services | MinIO, Presigned URL |
| **audit-log-service** | 8084 | Infra-Services | Async audit trail |
| **payment-gateway-service** | 8085 | Infra-Services | Telco MPS/Charging Core |
| **credit-wallet-service** | 8086 | Infra-Services | Redisson Lock |
| **crbt-campaign-service** | 8090 | Business | Gói cước, Lyria API |
| **crbt-community-library** | 8091 | Business | Kho nhạc, Fallback |
| **audio-generation-service** | 8092 | Business | Async job, max 5/user |
| **crbt-credit-transaction-service** | 8093 | Business | Lịch sử đối soát |
| **crbt-core-adapter** | 8094 | Business | CMS Mytone adapter |
| **eureka-server** | 8761 | Infrastructure | Service Registry |
| **config-server** | 8888 | Infrastructure | Centralized config |
| **ai-media-worker** | 8765 | Python AI | FastAPI + gRPC |
| **PostgreSQL** | 5432 | Data Store | |
| **Redis** | 6379 | Data Store | |
| **RabbitMQ AMQP** | 5672 | Data Store | 15672 = management UI |
| **MinIO API** | 9000 | Data Store | 9001 = console UI |
| **Prometheus** | 9090 | Monitoring | |
| **Grafana** | 3000 | Monitoring | admin/admin |
| **Loki** | 3100 | Monitoring | Log aggregation |

---

## Prerequisites

- **Java 21** (Eclipse Temurin hoặc OpenJDK)
- **Maven 3.9+**
- **Docker Desktop** (Windows/Mac) hoặc Docker Engine (Linux)
- **Docker Compose** v2.x
- **Python 3.11** (cho ai-media-worker)
- **Git**

---

## Quick Start

### 1. Clone Repository

```bash
git clone <repository-url>
cd Microservice-Platform
```

### 2. Cấu Hình Môi Trường

Copy `.env.example` thành `.env` và điền các giá trị thực:

```bash
cp .env.example .env
```

**QUAN TRỌNG**: Điền các giá trị sau trong `.env`:

```env
# Google Gemini Lyria 3
GEMINI_API_KEY=your-actual-api-key-here

# Mytone CMS
MYTONE_CMS_BASE_URL=https://cms.mytone.vn
MYTONE_CMS_API_KEY=your-cms-api-key

# MPS Payment Gateway
MPS_BASE_URL=https://mps.mytel.com.mm
MPS_MERCHANT_ID=your-merchant-id
MPS_SECRET_KEY=your-secret-key

# JWT Secret (generate strong random string)
JWT_SECRET=your-256-bit-secret-key-here
```

**KHÔNG commit file `.env` vào Git** (đã có trong `.gitignore`).

### 3. Khởi Động Data Stores + Monitoring

```bash
docker-compose up -d postgres redis rabbitmq minio prometheus grafana loki promtail
```

Đợi ~30s để các service khởi động hoàn tất. Kiểm tra:

```bash
docker-compose ps
```

Tất cả service phải ở trạng thái `healthy`.

### 4. Build Toàn Bộ Project

```bash
./mvnw clean install -DskipTests
```

Hoặc trên Windows:

```cmd
mvnw.cmd clean install -DskipTests
```

### 5. Khởi Động Services (Thứ Tự Bắt Buộc)

**Bước 1: Eureka Server** (Service Registry)

```bash
./mvnw spring-boot:run -pl infrastructure/eureka-server
```

Đợi log xuất hiện: `Started EurekaServerApplication in X seconds`

**Bước 2: Config Server** (Centralized Config)

```bash
./mvnw spring-boot:run -pl infrastructure/config-server
```

Đợi log: `Started ConfigServerApplication in X seconds`

**Bước 3: Infra Services** (có thể chạy song song trong các terminal riêng)

```bash
# Terminal 1
./mvnw spring-boot:run -pl infra-services/auth-service

# Terminal 2
./mvnw spring-boot:run -pl infra-services/notification-service

# Terminal 3
./mvnw spring-boot:run -pl infra-services/file-service

# Terminal 4
./mvnw spring-boot:run -pl infra-services/audit-log-service

# Terminal 5
./mvnw spring-boot:run -pl infra-services/payment-gateway-service

# Terminal 6
./mvnw spring-boot:run -pl infra-services/credit-wallet-service
```

**Bước 4: Business Services** (có thể chạy song song)

```bash
# Terminal 7
./mvnw spring-boot:run -pl business-services/crbt-campaign-service

# Terminal 8
./mvnw spring-boot:run -pl business-services/crbt-community-library

# Terminal 9
./mvnw spring-boot:run -pl business-services/audio-generation-service

# Terminal 10
./mvnw spring-boot:run -pl business-services/crbt-credit-transaction-service

# Terminal 11
./mvnw spring-boot:run -pl business-services/crbt-core-adapter
```

**Bước 5: Python AI Worker**

```bash
cd python-services/ai-media-worker
pip install -r requirements.txt
bash scripts/generate_protos.sh  # Generate gRPC stubs
uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```

**Bước 6: API Gateway** (khởi động sau cùng)

```bash
./mvnw spring-boot:run -pl infrastructure/api-gateway
```

### 6. Kiểm Tra Hệ Thống

- **Eureka Dashboard**: http://localhost:8761
- **API Gateway Health**: http://localhost:8080/actuator/health
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **MinIO Console**: http://localhost:9001 (minioadmin/minioadmin)

---

## Chạy Toàn Bộ Hệ Thống Bằng Docker Compose

Thay vì chạy từng service thủ công, có thể chạy toàn bộ hệ thống:

```bash
docker-compose up -d
```

**Lưu ý**: Lần đầu tiên sẽ mất ~5-10 phút để build tất cả Docker images.

Theo dõi logs:

```bash
docker-compose logs -f api-gateway
docker-compose logs -f crbt-campaign-service
```

Dừng toàn bộ:

```bash
docker-compose down
```

Dừng và xóa volumes (reset database):

```bash
docker-compose down -v
```

---

## Testing

### Unit Tests

```bash
# Tất cả services
./mvnw test

# 1 service cụ thể
./mvnw test -pl infra-services/auth-service

# 1 test class
./mvnw test -pl infra-services/auth-service -Dtest=AuthServiceTest
```

### Integration Tests

```bash
./mvnw verify -Pintegration-test
```

### Python Tests

```bash
cd python-services/ai-media-worker
pytest tests/
```

---

## API Documentation

Mỗi service expose Swagger UI tại `/swagger-ui.html`:

- Auth Service: http://localhost:8081/swagger-ui.html
- File Service: http://localhost:8083/swagger-ui.html
- Campaign Service: http://localhost:8090/swagger-ui.html
- Audio Generation: http://localhost:8092/swagger-ui.html

**Qua API Gateway** (production):

- http://localhost:8080/auth-service/swagger-ui.html
- http://localhost:8080/crbt-campaign-service/swagger-ui.html

---

## Observability

### Metrics (Prometheus)

Truy cập: http://localhost:9090

Các metrics quan trọng:

- `http_server_requests_seconds_count` — Request count
- `http_server_requests_seconds_sum` — Total latency
- `resilience4j_circuitbreaker_state` — Circuit breaker state
- `jvm_memory_used_bytes` — JVM memory usage

### Dashboards (Grafana)

Truy cập: http://localhost:3000 (admin/admin)

Import dashboards từ `infrastructure/monitoring-server/dashboards/`:

1. JVM Micrometer Dashboard (ID: 4701)
2. Spring Boot Statistics (ID: 6756)
3. RabbitMQ Overview (ID: 10991)

### Logs (Loki)

Trong Grafana, chọn datasource **Loki**, query:

```logql
{app="crbt-campaign-service"} |= "ERROR"
```

Tất cả logs đều có `traceId` và `spanId` để trace request qua nhiều service.

### Distributed Tracing

Mỗi request qua Gateway được gán `traceId`. Tìm trong logs:

```logql
{app=~".+"} | json | traceId="abc123def456"
```

### Alerts

Prometheus alert rules đã được cấu hình trong `infrastructure/monitoring-server/crbt-monitoring-rules.yml`:

- **HighAICost**: Cảnh báo khi chi phí AI vượt ngưỡng
- **HighServiceErrorRate**: Cảnh báo khi error rate > 5%

---

## Common Tasks

### Thêm User Mới

```bash
curl -X POST http://localhost:8080/auth-service/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "959123456789",
    "email": "user@example.com",
    "fullName": "John Doe"
  }'
```

### Nạp Credit Vào Ví

```bash
curl -X POST http://localhost:8080/credit-wallet-service/api/v1/wallets/topup \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <user-id>" \
  -d '{
    "amount": 10000,
    "transactionId": "TXN123456"
  }'
```

### Subscribe Gói Cước

```bash
curl -X POST http://localhost:8080/crbt-campaign-service/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <user-id>" \
  -d '{
    "packageId": "<package-id>"
  }'
```

### Tạo Nhạc Chờ AI

```bash
curl -X POST http://localhost:8080/audio-generation-service/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <user-id>" \
  -d '{
    "genre": "pop",
    "mood": "happy",
    "instrument": "guitar",
    "duration": 30
  }'
```

Trả về `jobId`. Kiểm tra trạng thái:

```bash
curl http://localhost:8080/audio-generation-service/api/v1/jobs/<job-id> \
  -H "X-User-Id: <user-id>"
```

---

## Troubleshooting

### Service không register vào Eureka

**Triệu chứng**: Service khởi động nhưng không xuất hiện trong Eureka Dashboard.

**Giải pháp**:
1. Kiểm tra `eureka-server` đã chạy chưa
2. Kiểm tra log service có lỗi kết nối Eureka không
3. Kiểm tra `application.yml` có đúng `eureka.client.service-url.defaultZone`

### RabbitMQ Connection Refused

**Triệu chứng**: `Connection refused: localhost:5672`

**Giải pháp**:
```bash
docker-compose ps rabbitmq
# Nếu unhealthy, restart:
docker-compose restart rabbitmq
```

### PostgreSQL Connection Error

**Triệu chứng**: `Connection to localhost:5432 refused`

**Giải pháp**:
```bash
docker-compose ps postgres
# Kiểm tra logs:
docker-compose logs postgres
# Restart nếu cần:
docker-compose restart postgres
```

### Circuit Breaker Open

**Triệu chứng**: API trả về `503 Service Unavailable` với message `CircuitBreaker 'xxx' is OPEN`

**Giải pháp**:
1. Kiểm tra service downstream có chạy không
2. Xem Prometheus metrics: `resilience4j_circuitbreaker_state{name="xxx"}`
3. Đợi circuit breaker tự động chuyển sang HALF_OPEN (30s)

### MinIO Bucket Not Found

**Triệu chứng**: `The specified bucket does not exist`

**Giải pháp**:
1. Truy cập MinIO Console: http://localhost:9001
2. Login: minioadmin/minioadmin
3. Tạo bucket `crbt-files` thủ công
4. Hoặc service sẽ tự tạo khi khởi động (nếu có quyền)

### Python AI Worker gRPC Error

**Triệu chứng**: `ModuleNotFoundError: No module named 'generated'`

**Giải pháp**:
```bash
cd python-services/ai-media-worker
bash scripts/generate_protos.sh
```

### Out of Memory (Java)

**Triệu chứng**: `java.lang.OutOfMemoryError: Java heap space`

**Giải pháp**:
Tăng heap size khi chạy:
```bash
JAVA_OPTS="-Xmx2g" ./mvnw spring-boot:run -pl <service>
```

Hoặc trong Dockerfile:
```dockerfile
ENTRYPOINT ["java", "-Xmx1g", "-jar", "app.jar"]
```

---

## Project Structure

```
D:\Microservice-Platform\
├── pom.xml                          # Parent POM
├── docker-compose.yml               # Full-stack orchestration
├── .env.example                     # Environment template
├── .env                             # Actual secrets (gitignored)
├── CLAUDE.md                        # AI coding instructions
├── CONVENTIONS.md                   # Code conventions
├── README.md                        # This file
├── docs/
│   ├── PLAN.md                      # Sprint plan
│   ├── CHECKPOINT.md                # Progress tracker
│   └── TASKS.md                     # Task breakdown
├── common/
│   ├── common-core/                 # ApiResponse, ErrorResponse, Resilience4j
│   ├── common-security/             # JWT filter, SecurityUtils
│   ├── common-ai-sdk/               # Lyria prompts, TTS metadata
│   └── common-rmq/                  # RabbitMQ config, DLQ
├── infrastructure/
│   ├── eureka-server/               # Service registry (8761)
│   ├── config-server/               # Config center (8888)
│   ├── api-gateway/                 # API Gateway (8080)
│   └── monitoring-server/           # Prometheus + Grafana configs
├── infra-services/
│   ├── auth-service/                # Authentication (8081)
│   ├── notification-service/        # Notifications (8082)
│   ├── file-service/                # File storage (8083)
│   ├── audit-log-service/           # Audit trail (8084)
│   ├── payment-gateway-service/     # Payment (8085)
│   └── credit-wallet-service/       # Wallet (8086)
├── business-services/
│   ├── crbt-campaign-service/       # Campaigns (8090)
│   ├── crbt-community-library/      # Ringtone library (8091)
│   ├── audio-generation-service/    # AI audio jobs (8092)
│   ├── crbt-credit-transaction-service/ # Transactions (8093)
│   └── crbt-core-adapter/           # CMS adapter (8094)
└── python-services/
    └── ai-media-worker/             # AI processing (8765)
        ├── main.py                  # FastAPI + gRPC bootstrap
        ├── proto/ai_media.proto     # gRPC contract
        ├── app/
        │   ├── config.py
        │   ├── grpc_server.py
        │   ├── routers/ai_media.py
        │   └── services/
        │       ├── chorus_detector.py
        │       ├── audio_separator.py
        │       └── tts_service.py
        └── tests/
```

---

## Development Workflow

### 1. Tạo Feature Branch

```bash
git checkout -b feature/new-feature
```

### 2. Code + Test

```bash
# Chạy service đang phát triển
./mvnw spring-boot:run -pl <service>

# Chạy tests
./mvnw test -pl <service>
```

### 3. Commit

```bash
git add .
git commit -m "feat(service): add new feature"
```

Commit message format: `<type>(<scope>): <subject>`

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

### 4. Push + Pull Request

```bash
git push origin feature/new-feature
```

Tạo Pull Request trên GitHub/GitLab.

---

## Production Deployment

### Build Production Images

```bash
# Build tất cả services
docker-compose build

# Build 1 service
docker-compose build crbt-campaign-service
```

### Push to Registry

```bash
docker tag microservice-platform-crbt-campaign-service:latest \
  registry.example.com/crbt-campaign-service:v1.0.0

docker push registry.example.com/crbt-campaign-service:v1.0.0
```

### Deploy to Kubernetes

Helm charts nằm trong `k8s/helm/`:

```bash
helm install crbt-platform ./k8s/helm/crbt-platform \
  --namespace production \
  --values ./k8s/helm/crbt-platform/values-prod.yaml
```

---

## Security Considerations

1. **JWT Secret**: Phải là chuỗi random 256-bit, không được hardcode.
2. **Database Passwords**: Dùng secrets management (Vault, AWS Secrets Manager).
3. **API Keys**: Gemini API key, Mytone CMS key phải được encrypt trong `.env`.
4. **Rate Limiting**: API Gateway có rate limit 100 req/min/user (có thể điều chỉnh).
5. **HTTPS**: Production phải dùng HTTPS với valid certificate.
6. **CORS**: API Gateway chỉ cho phép origins trong whitelist.

---

## Performance Tuning

### JVM Options (Production)

```bash
JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Database Connection Pool

Trong `application.yml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### Redis Connection Pool

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```

### RabbitMQ Prefetch

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 10
```

---

## License

Proprietary - Mytel Myanmar

---

## Contact

- **Team Lead**: [Your Name]
- **Email**: team@mytel.com.mm
- **Slack**: #crbt-platform

---

## Changelog

### v1.0.0 (2026-05-22)
- Initial release
- 11 Java microservices
- 1 Python AI worker
- Full observability stack
- Docker Compose orchestration

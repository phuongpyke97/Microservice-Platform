# Microservice Platform — CLAUDE.md

> Đọc file này là hiểu đủ để code. Không hỏi thêm.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Java services | Spring Boot 3.2.x · Java 21 · Maven multi-module |
| Python service | FastAPI · gRPC · Python 3.11 |
| Database | PostgreSQL 16 (mỗi service 1 DB riêng) |
| Cache / Lock | Redis Cluster 7.x + Redisson |
| Message broker | RabbitMQ 3.12 |
| Object storage | MinIO (S3-compatible) |
| Log aggregation | Grafana Loki + Promtail |
| Metrics | Prometheus + Grafana |
| Service registry | Netflix Eureka (Spring Cloud) |
| Config center | Spring Cloud Config Server |
| API Gateway | Spring Cloud Gateway |
| Circuit breaker | Resilience4j |
| AI music | Google Gemini Lyria 3 |
| TTS | Microsoft Edge TTS (WebSocket stream) |
| Audio separation | Spleeter / Demucs |
| Auth model | CRBT Token (JWT từ nhà mạng) → verify local bằng shared secret → bóc tách X-MSISDN, X-Subscription-Type |

---

## Port Map

| Service | Port | Layer | Note |
|---|---|---|---|
| api-gateway | 8080 | Infrastructure | Cổng duy nhất từ Client |
| auth-service | 8081 | Infra-Services | JWT, BCrypt strength 12 |
| notification-service | 8082 | Infra-Services | RabbitMQ consumer only, no HTTP |
| file-service | 8083 | Infra-Services | MinIO, Presigned URL |
| audit-log-service | 8084 | Infra-Services | Async audit trail |
| payment-gateway-service | 8085 | Infra-Services | Telco MPS/Charging Core |
| credit-wallet-service | 8086 | Infra-Services | Redisson Lock |
| crbt-campaign-service | 8090 | Business | Gói cước, Lyria API |
| crbt-community-library | 8091 | Business | Kho nhạc, Fallback |
| audio-generation-service | 8092 | Business | Async job, max 5/user |
| crbt-credit-transaction-service | 8093 | Business | Lịch sử đối soát tài chính |
| crbt-core-adapter | 8094 | Business | CMS Mytone adapter |
| eureka-server | 8761 | Infrastructure | Service Registry |
| config-server | 8888 | Infrastructure | Centralized config |
| ai-media-worker | 8765 | Python AI | FastAPI + gRPC |
| PostgreSQL | 5432 | Data Store | |
| Redis Cluster | 6379 | Data Store | |
| RabbitMQ AMQP | 5672 | Data Store | 15672 = management UI |
| MinIO API | 9000 | Data Store | 9001 = console UI |

---

## Cấu Trúc Project

```
D:\Microservice-Platform\
├── pom.xml                          ← Parent POM, quản lý dependency tập trung
├── docker-compose.yml               ← PostgreSQL, Redis, RabbitMQ, MinIO, Loki
├── .env                             ← Biến môi trường tập trung
├── CLAUDE.md                        ← File này
├── CONVENTIONS.md
├── common/
│   ├── common-core/                 ← ApiResponse, ErrorResponse, PageResponse, GlobalExceptionHandler, Resilience4j
│   ├── common-security/             ← JwtAuthenticationFilter, SecurityUtils, X-header injection
│   ├── common-ai-sdk/               ← Lyria System Prompt, Edge TTS voice metadata
│   └── common-rmq/                  ← RabbitMQ RetryTemplate, DLQ config, exchange/queue constants
├── infrastructure/
│   ├── eureka-server/               ← Port 8761
│   ├── config-server/               ← Port 8888
│   ├── api-gateway/                 ← Port 8080
│   └── monitoring-server/           ← Prometheus + Grafana Loki
├── infra-services/
│   ├── auth-service/                ← Port 8081
│   ├── notification-service/        ← Port 8082
│   ├── file-service/                ← Port 8083
│   ├── audit-log-service/           ← Port 8084
│   ├── payment-gateway-service/     ← Port 8085
│   └── credit-wallet-service/       ← Port 8086
├── business-services/
│   ├── crbt-campaign-service/       ← Port 8090
│   ├── crbt-community-library/      ← Port 8091
│   ├── audio-generation-service/    ← Port 8092
│   ├── crbt-credit-transaction-service/ ← Port 8093
│   └── crbt-core-adapter/           ← Port 8094
└── python-services/
    └── ai-media-worker/             ← Port 8765, FastAPI + gRPC
```

---

## Package Structure Chuẩn

Tất cả Java service theo chuẩn:

```
com.platform.{service}.
├── config/          ← Spring config beans (SecurityConfig, RabbitConfig, FeignConfig...)
├── controller/      ← @RestController — chỉ nhận/trả DTO, không có logic
├── service/         ← Business logic thuần, gọi repository và external client
├── repository/      ← JpaRepository interfaces
├── entity/          ← @Entity JPA
├── dto/
│   ├── request/     ← DTO nhận vào
│   └── response/    ← DTO trả ra
├── event/           ← RabbitMQ event POJO
├── listener/        ← @RabbitListener handlers
├── client/          ← Feign clients gọi service khác
├── exception/       ← Custom exceptions, error codes
└── util/            ← Utility classes
```

Ví dụ cụ thể:
- `com.platform.auth` — auth-service
- `com.platform.fileservice` — file-service
- `com.platform.creditwallet` — credit-wallet-service
- `com.platform.audiogeneration` — audio-generation-service

---

## Common Modules (JAR dùng chung)

### common-core
- `ApiResponse<T>` — wrapper mọi response thành công
- `ErrorResponse` — format lỗi thống nhất (errorCode, message, timestamp)
- `PageResponse<T>` — list API có phân trang
- `GlobalExceptionHandler` — bắt toàn bộ exception → trả ErrorResponse đúng format
- Resilience4j config beans
- `DebugLoggingAspect` — AOP aspect tự động log `Start/End/Error` (DEBUG level) cho Controller/Service
- `AutoAuditLogAspect` — AOP aspect tự động publish `AuditLogEvent` (SUCCESS/FAILED) lên RabbitMQ cho mọi API call

### common-security
- `JwtAuthenticationFilter` — đọc X-User-Id, X-User-Email, X-User-Roles từ header
- `SecurityUtils.getCurrentUserId()` — lấy userId từ SecurityContext
- `MDCFilter` — Spring filter bóc `X-Correlation-ID` từ header vào MDC (`traceId`)
- `FeignCorrelationInterceptor` — Feign interceptor propagate `X-Correlation-ID` khi gọi Feign client
- Không validate JWT lại (Gateway đã validate)

### common-ai-sdk
- `LyriaSystemPromptConfig` — System Prompt tối ưu cho Google Gemini Lyria 3
- `EdgeTtsVoiceMetadata` — danh sách giọng: vi-VN-HoaiMyNeural, vi-VN-NamMinhNeural, my-MM-ThihaNeural

### common-rmq
- RetryTemplate 3 lần: backoff 1s, 2s, 4s
- DLQ config tự động
- Constants: `RmqExchanges`, `RmqQueues`, `RmqRoutingKeys`

---

## Lệnh Chạy Từng Service

### Khởi động hạ tầng (bắt buộc trước)
```bash
docker-compose up -d
```

### Build toàn bộ project
```bash
./mvnw clean install -DskipTests
```

### Build 1 service
```bash
./mvnw clean install -DskipTests -pl infrastructure/eureka-server
```

### Chạy service theo thứ tự (bắt buộc đúng thứ tự)
```bash
# 1. Eureka
./mvnw spring-boot:run -pl infrastructure/eureka-server

# 2. Config Server
./mvnw spring-boot:run -pl infrastructure/config-server

# 3. Các service khác (có thể song song)
./mvnw spring-boot:run -pl infra-services/auth-service
./mvnw spring-boot:run -pl infra-services/file-service
./mvnw spring-boot:run -pl infra-services/credit-wallet-service
# ... các service còn lại

# 4. API Gateway (khởi động sau cùng)
./mvnw spring-boot:run -pl infrastructure/api-gateway
```

### Chạy test
```bash
# Tất cả
./mvnw test

# 1 service
./mvnw test -pl infra-services/auth-service

# 1 class
./mvnw test -pl infra-services/auth-service -Dtest=AuthServiceTest
```

### Python AI Worker
```bash
cd python-services/ai-media-worker
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```

---

## Quy Tắc Bắt Buộc (KHÔNG được vi phạm)

1. **Response format**: Mọi API đều wrap trong `ApiResponse<T>` từ common-core. Không return raw object.

2. **Error format**: Mọi exception phải qua `GlobalExceptionHandler`, trả `ErrorResponse`. Không return raw error.

3. **Package**: Đúng `com.platform.{service}.{layer}`. Không đặt sai package.

4. **JWT**: Không validate JWT trong service nội bộ. Gateway chịu trách nhiệm validate Admin/CMS JWT và CRBT JWT, rồi inject header nội bộ.

5. **Redisson Lock**: credit-wallet-service PHẢI dùng Redisson Lock key `wallet:{userId}` khi trừ/cộng credit. Không bỏ qua.

6. **Async job**: audio-generation-service trả 202 ngay, không block. Job chạy nền qua `@Async`.

7. **DB isolation**: Mỗi service có DB riêng. Không service nào query DB của service khác.

8. **RabbitMQ DLQ**: Mọi consumer PHẢI config DLQ qua common-rmq. Không để message mất.

9. **Feign + Circuit Breaker**: Mọi Feign client gọi service khác PHẢI có fallback Resilience4j.

10. **Flyway**: Migration file đặt trong `src/main/resources/db/migration/`, naming: `V{n}__{description}.sql`.

11. **Error code**: Theo format `SERVICE_ERROR_CODE`, ví dụ `AUTH_USER_NOT_FOUND`, `WALLET_INSUFFICIENT_CREDIT`.

12. **notification-service**: Không mở HTTP endpoint ra ngoài. Chỉ nhận message từ RabbitMQ.

13. **audit-log-service**: Không gọi đồng bộ từ luồng chính. Luôn qua RabbitMQ async. (Đã được cover tự động bởi `AutoAuditLogAspect` ở `common-core`).

14. **Immutable records**: crbt-credit-transaction-service không được UPDATE/DELETE record sau khi INSERT.

15. **Request Tracing**: Request qua Gateway tự động sinh `X-Correlation-ID`. Header này truyền xuyên suốt các service qua Feign và được lưu vào MDC để log. Mọi log mới tự động in kèm traceId. Cấm ghi đè MDC traceId thủ công.

## CodeGraph

This project uses CodeGraph as the local semantic code graph for AI-assisted navigation.

Rules:
- For codebase questions, prefer CodeGraph queries before broad file browsing.
- Keep the CodeGraph index current after code changes.
- CodeGraph is local-only and used through its Claude Code/MCP integration.

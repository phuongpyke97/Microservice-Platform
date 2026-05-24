# KẾ HOẠCH DỰ ÁN — Microservice Platform (Mytel CRBT)

> Nguồn: CLAUDE.md, CONVENTIONS.md, TASKS.md, Architecture.md.
> Trạng thái repo khi lập plan: chỉ có docs, chưa có code.
> Lập ngày: 2026-05-21.

---

## QUYẾT ĐỊNH ĐÃ CHỐT

- **Auth model: HYBRID.** API Gateway hỗ trợ cả 2 luồng:
  - Subscriber → CRBT Token → Gateway verify với nhà mạng (shared secret) → lazy-create user theo MSISDN → inject `X-User-Id`, `X-MSISDN`, `X-Subscription-Type`.
  - Admin/CMS → JWT truyền thống → auth-service có đăng ký/đăng nhập/refresh/quên mật khẩu → inject `X-User-Id`, `X-User-Email`, `X-User-Roles`.
  - Service nội bộ không phân biệt nguồn — chỉ đọc header qua `common-security`.

---

## QUY TẮC LÀM VIỆC

| Khi | Hành động |
|---|---|
| Xong 1 task nhỏ (Tx.y) | Đánh dấu done, qua task kế. Không test/review/report — tiết kiệm token. |
| Xong 1 Sprint | Viết test phần logic cốt lõi + self-review theo 14 rule CLAUDE.md + report ngắn. |
| Môi trường | Máy Windows không chạy được — chỉ kiểm tra tĩnh: compile-logic, convention, contract. |

---

## SƠ ĐỒ PHỤ THUỘC

```
S0 Khung dự án
 └─ S1 Common SDK ───────── dependency biên dịch của TẤT CẢ
     └─ S2 Infrastructure ── dependency runtime
         ├─ S3 Auth + Credit-Wallet
         ├─ S4 File + Payment-Gateway
         ├─ S5 Notification + Audit-Log
         └─ S6 ai-media-worker (Python)
             └─ S7 Credit-Transaction + Community-Library
                 └─ S8 Campaign-Service
                     └─ S9 Audio-Generation + Core-Adapter
                         └─ S10 Tích hợp E2E + Observability
                             └─ S11 Hardening + bàn giao
```
S3, S4, S5, S6 độc lập nhau — đều chỉ cần xong S2.

---

## SPRINT 0 — Khung dự án
- T0.1 Parent pom.xml: multi-module, Java 21, Spring Boot 3.2.x BOM, Spring Cloud BOM, khai báo 22 module.
- T0.2 docker-compose.yml: PostgreSQL 16, Redis Cluster 7, RabbitMQ 3.12, MinIO, Loki + Promtail (healthcheck, depends_on).
- T0.3 .env + .env.example.
- T0.4 Skeleton 22 module + pom.xml rỗng mỗi module.
- T0.5 .gitignore + git init.
- Chốt: review cấu trúc khớp CLAUDE.md → report.

## SPRINT 1 — Common SDK (đóng băng contract sau sprint này)
common-core:
- T1.1 ApiResponse<T>
- T1.2 ErrorResponse
- T1.3 PageResponse<T>
- T1.4 ErrorCode interface + BaseException
- T1.5 GlobalExceptionHandler (@RestControllerAdvice)
- T1.6 Resilience4j config beans + Feign ErrorDecoder
common-security:
- T1.7 JwtAuthenticationFilter (đọc X-User-Id/Email/Roles, nạp SecurityContextHolder)
- T1.8 SecurityUtils
- T1.9 SecurityConfig stateless dùng chung
common-rmq:
- T1.10 Constants RmqExchanges / RmqQueues / RmqRoutingKeys
- T1.11 RetryTemplate 3 lần (1s/2s/4s) + DLQ auto-config
- T1.12 Event POJO dùng chung: UserRegisteredEvent, CreditChangedEvent, AudioGeneratedEvent, PaymentResultEvent, ... (điểm tránh rework chính)
common-ai-sdk:
- T1.13 LyriaSystemPromptConfig
- T1.14 EdgeTtsVoiceMetadata (vi-VN-HoaiMyNeural, vi-VN-NamMinhNeural, my-MM-ThihaNeural)
- Chốt: unit test ApiResponse/ErrorResponse/SecurityUtils/RetryTemplate + review contract + report.

## SPRINT 2 — Infrastructure
- T2.1 eureka-server (8761) + Basic Auth dashboard.
- T2.2 config-server (8888) native profile + repo config {service}-{profile}.yml cho 14 service.
- T2.3 api-gateway (8080): routing prefix, CRBT-token verify + JWT verify (hybrid), inject header, rate-limit Redis token bucket, CORS.
- T2.4 monitoring-server: Prometheus scrape + Grafana datasource + Loki/Promtail.
- T2.5 Dockerfile 4 service hạ tầng.
- Chốt: review config + report (kiểm tra tĩnh).

## SPRINT 3 — auth-service (8081) + credit-wallet-service (8086)
auth-service (com.platform.auth):
- T3.1 entity User (msisdn, email, password_hash, roles, credit_balance, status)
- T3.2 Flyway V1__create_users_table.sql
- T3.3 repository
- T3.4 service: lazy-create theo MSISDN (credit_balance=2 trial), BCrypt strength 12, login/register/refresh/forgot-password (admin)
- T3.5 controller
- T3.6 publish user.registered
- T3.7 ErrorCode enum AUTH_*
credit-wallet-service (com.platform.creditwallet):
- T3.8 entity Wallet
- T3.9 Flyway migration
- T3.10 repository
- T3.11 service: Redisson Lock wallet:{userId} timeout 3s + SELECT FOR UPDATE
- T3.12 controller
- T3.13 listener payment.success → cộng credit
- T3.14 publish credit.deducted
- T3.15 ErrorCode enum WALLET_*
- Chốt: test race condition lock, timeout 3s, chặn trừ âm, trial-grant 2 credit + review + report.

## SPRINT 4 — file-service (8083) + payment-gateway-service (8085)
file-service (com.platform.fileservice):
- T4.1 entity FileMetadata
- T4.2 Flyway
- T4.3 MinIO client config
- T4.4 upload nhỏ ≤5MB whitelist jpg/png/mp3/wav/ogg
- T4.5 presigned PUT/GET URL
- T4.6 confirm move bucket temp → đích
- T4.7 soft-delete (status=DELETED)
- T4.8 ErrorCode FILE_*
payment-gateway-service (com.platform.payment):
- T4.9 entity + Flyway
- T4.10 Feign client MPS + Resilience4j fallback
- T4.11 charge flow + idempotency key
- T4.12 publish payment.result
- T4.13 reconciliation log
- T4.14 ErrorCode PAY_*
- Chốt: test idempotency, presigned TTL, fallback circuit + review + report.

## SPRINT 5 — notification-service (8082) + audit-log-service (8084)
notification-service (consumer thuần, KHÔNG HTTP endpoint):
- T5.1 listener user.registered / password.reset / subscription.success / audio.generated
- T5.2 Email + SMS sender
- T5.3 DLQ + retry
notification: không khai HTTP endpoint ra ngoài.
audit-log-service (DB riêng audit_db):
- T5.5 entity AuditLog
- T5.6 Flyway
- T5.7 listener login.failed / password.changed / credit.deducted / subscription.charged / admin.action
- T5.8 API nội bộ /audit/query
- T5.9 DLQ
- Chốt: test DLQ flow (3 fail → DLQ), query filter + review + report.

## SPRINT 6 — ai-media-worker (Python, 8765)
- T6.1 cấu trúc project + requirements.txt + venv
- T6.2 .proto 3 service gRPC + sinh stub
- T6.3 API 1 dò điệp khúc (NumPy SSM vectorized)
- T6.4 API 2 tách âm (Spleeter/Demucs)
- T6.5 API 3 Edge TTS (WebSocket stream)
- T6.6 FastAPI HTTP wrapper + gRPC server
- T6.7 Dockerfile
- Chốt: pytest 3 API + review + report.

## SPRINT 7 — crbt-credit-transaction-service (8093) + crbt-community-library (8091)
crbt-credit-transaction-service:
- T7.1 entity immutable (@Immutable, không UPDATE/DELETE)
- T7.2 Flyway
- T7.3 listener credit.changed
- T7.4 API history phân trang
- T7.5 API export CSV/Excel
- T7.6 ErrorCode
crbt-community-library:
- T7.7 entity LibraryTrack
- T7.8 Flyway
- T7.9 cache theo hash(genre+mood+instrument)
- T7.10 API /library/fallback random
- T7.11 lưu kết quả Lyria vào library
- T7.12 ErrorCode
- Chốt: test immutability, fallback random + review + report.

## SPRINT 8 — crbt-campaign-service (8090)
- T8.1 entity Package + Subscription
- T8.2 Flyway
- T8.3 rule engine credit_bonus
- T8.4 đăng ký gói (Feign payment + wallet, có fallback)
- T8.5 webhook subscription-event
- T8.6 gọi Lyria (System Prompt + daily quota + circuit breaker)
- T8.7 scheduler auto-renew 00:00
- T8.8 ErrorCode
- Chốt: test rule engine, fallback Lyria→library + review + report.

## SPRINT 9 — audio-generation-service (8092) + crbt-core-adapter (8094)
audio-generation-service:
- T9.1 AsyncConfig audioJobExecutor (core 10 / max 30 / queue 200)
- T9.2 endpoint 202 Accepted + jobId
- T9.3 guard credit + concurrent ≤5 (Redis counter job:{userId}:active)
- T9.4 luồng AI
- T9.5 luồng DIY (gRPC ai-media-worker 3 API)
- T9.6 progress Redis job:{jobId}:progress
- T9.7 polling GET status
- T9.8 Feign client mọi service + fallback
- T9.9 publish audio.generated
crbt-core-adapter:
- T9.10 tải MinIO + transcode 128kbps ID3 tags
- T9.11 POST CMS Mytone upload + register
- T9.12 retry 5 lần (5s/15s/45s) → DLQ
- T9.13 lưu mapping userId↔songId
- Chốt: test guard, job state machine, adapter retry + review + report.

## SPRINT 10 — Tích hợp E2E + Observability
- T10.1 rà toàn bộ Feign client có fallback (rule 9)
- T10.2 rà toàn bộ consumer có DLQ (rule 8)
- T10.3 actuator + Prometheus mọi service
- T10.4 JSON logging + correlation/trace ID xuyên Gateway/Feign/RMQ
- T10.5 Grafana dashboard + alert (AI cost, error rate)
- Chốt: integration test 3 luồng chính (AI / DIY / nạp credit) + review + report.

## SPRINT 11 — Hardening + bàn giao
- T11.1 audit error code toàn hệ thống (SERVICE_CATEGORY_CODE)
- T11.2 audit naming (kebab endpoint, snake_case DB)
- T11.3 Dockerfile + compose toàn bộ
- T11.4 README chạy + thứ tự khởi động
- T11.5 cập nhật CHECKPOINT.md + TASKS.md
- Chốt: review tổng + report cuối.

# CHECKPOINT

## Last updated
2026-05-22

## Current focus
Sprint 12 — Gap Closure [DONE]. All 12 sprints complete. System ready for integration testing.

## What was done

### Sprint 0 - Khung dự án [DONE]
- Parent pom.xml, docker-compose, .env, skeleton 22 module.

### Sprint 1 - Common SDK [DONE]
- common-core: ApiResponse, ErrorResponse, BaseException, GlobalExceptionHandler, Resilience4j config.
- common-security: JwtAuthenticationFilter, SecurityUtils, SecurityConfig.
- common-rmq: RmqExchanges/Queues/RoutingKeys, RetryTemplate 3 lần, DLQ, Event POJOs.
- common-ai-sdk: LyriaSystemPromptConfig, EdgeTtsVoiceMetadata.

### Sprint 2 - Infrastructure [DONE]
- eureka-server (8761), config-server (8888), api-gateway (8080).
- monitoring-server: Prometheus, Grafana, Loki, Promtail.
- Dockerfile cho 4 infra services.

### Sprint 3 - Auth + Credit Wallet [DONE]
- auth-service (8081): User entity, JWT, BCrypt, lazy-create by MSISDN.
- credit-wallet-service (8086): Wallet entity, Redisson Lock, payment listener.

### Sprint 4 - File + Payment [DONE]
- file-service (8083): MinIO, presigned URL, whitelist type, soft-delete.
- payment-gateway-service (8085): MPS Feign client, circuit breaker, idempotency.

### Sprint 5 - Notification + Audit [DONE]
- notification-service (8082): Consumer-only, xử lý user/payment/audio events.
- audit-log-service (8084): Async consumer, query API, DLQ.

### Sprint 6 - AI Media Worker (Python) [DONE]
- ai-media-worker (8765): FastAPI + gRPC, chorus detector, audio separator, Edge TTS.
- POST /generate-tts endpoint added for Java orchestration.

### Sprint 7 - Credit Transaction + Library [DONE]
- crbt-credit-transaction-service (8093): Immutable record, listener, pagination/export.
- crbt-community-library (8091): Ringtone library, fallback random API.

### Sprint 8 - Campaign Service [DONE]
- crbt-campaign-service (8090): Campaign/Package/Subscription, subscribe flow, credit event.
- LyriaClient: Gọi Gemini Lyria 3 API với circuit breaker.
- LyriaService: System Prompt, fallback sang library.
- LibraryClient: Feign client với fallback.

### Sprint 9 - Audio Generation + Core Adapter [DONE]
- audio-generation-service (8092): Async job, max 5 active/user, TTS orchestration.
- crbt-core-adapter (8094): Mytone CMS RestClient, circuit breaker + retry.

### Sprint 10 - Observability [DONE]
- micrometer-tracing-bridge-brave, JSON logging via logstash-logback-encoder.
- Prometheus alert rules: HighAICost, HighServiceErrorRate.
- Prometheus scrape config cho 14 services.

### Sprint 11 - Hardening [DONE]
- Dockerfile cho tất cả 11 Java services.
- docker-compose.yml full hệ thống (data store + infra + app + Python).
- T11.1 audit error codes: Fix CampaignErrorCode (PACKAGE_NOT_FOUND → CAMPAIGN_PACKAGE_NOT_FOUND, SUBSCRIPTION_NOT_FOUND → CAMPAIGN_SUBSCRIPTION_NOT_FOUND); fix CreditTransactionErrorCode enum constant name.
- T11.2 audit naming: Toàn bộ REST endpoint dùng kebab-case ✓. Entity @Column dùng snake_case ✓.
- T11.4 README.md root: setup, port map, startup order, observability, troubleshooting, deployment.

### Sprint 12 - Gap Closure [DONE]
- S12.1 Campaign auto-renew scheduler: @Scheduled(cron = "0 0 0 * * *"), bonus rule engine (+10% for packages >1000), Flyway migration for auto_renew column.
- S12.2 Library cache + fallback: @Cacheable on searchRingtones, random API by genre with fallback, @EnableCaching.
- S12.3 Audio Gen refactor: Redis counters (INCR/DECR) replace DB query, progress tracking via Redis, AsyncConfig updated (10/30/200), DIY flow placeholder.
- S12.4 Core Adapter: Exponential backoff config (1s * 2^n), transcode placeholder (128kbps MP3 + ID3), DLQ routing after 3 retries.
- S12.5 Credit Tx export: CSV export endpoint /export with Content-Disposition header.

## Blockers
Không có.

## Next session start from
All sprints complete. PRD gaps closed. Next: E2E integration tests, load testing, K8s deployment manifests.

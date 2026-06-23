# CMS Admin API & System Resilience UAT Test Cases

This document defines the User Acceptance Testing (UAT) scenarios for the **Lyria Prompt Manager**, **Credit Transaction Management**, and the system resilience and transactional integrity fixes.

---

## 1. Lyria Prompt Manager UAT
**Goal**: Verify that administrators can manage prompts, view histories, edit templates, and that caching performs correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | GET `/crbt-campaign-service/campaigns/admin/lyria-prompts/active?model=lyria-3-clip-preview` with header `X-User-Roles: ADMIN`. | Returns 200 OK with the active prompt details. | |
| 1.2 | GET `/crbt-campaign-service/campaigns/admin/lyria-prompts/active` with no headers or with `X-User-Roles: USER`. | Returns 403 Forbidden, access denied. | |
| 1.3 | POST `/crbt-campaign-service/campaigns/admin/lyria-prompts` with valid `UpdateLyriaPromptRequest` JSON payload. | Returns 200 OK; the new prompt version is saved, marked `ACTIVE`, and the previous version is deactivated. | |
| 1.4 | POST with a prompt template containing wrong formatting placeholders (not 7 `%s` and 1 `%d`). | Returns 400 Bad Request (`COMMON_BAD_REQUEST`). | |
| 1.5 | GET `/crbt-campaign-service/campaigns/admin/lyria-prompts/history` with `model=ALL`. | Returns 200 OK with a list of all versions (active at the top, then newest first). | |
| 1.6 | GET `/crbt-campaign-service/campaigns/admin/lyria-prompts/versions/{model}/{version}` for an existing version. | Returns 200 OK with full prompt version details. | |
| 1.7 | PUT `/crbt-campaign-service/campaigns/admin/lyria-prompts/versions/{model}/{version}/activate`. | Returns 200 OK; the specified version is reactivated (status `ACTIVE`) and the other is deactivated. | |
| 1.8 | **Cache Verification**: Run GET `/active` multiple times and inspect logs. | First request reads from DB; subsequent requests hit Redis cache directly (Spring AOP proxy fix verified). | |

---

## 2. Credit Transaction Management UAT
**Goal**: Verify that users and administrators can audit transactional records and export them.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | GET `/crbt-credit-transaction-service/credit-transactions/history` with header `X-User-Id: 8` and `X-User-Roles: USER`. | Returns 200 OK with paginated list of transaction history sorted by newest first. | |
| 2.2 | GET `/credit-transactions/history` without `X-User-Id` header. | Returns 401 Unauthorized. | |
| 2.3 | GET `/credit-transactions/history` with query filters `direction=DEDUCT` and `reason=AI`. | Returns 200 OK filtering transactions exactly by criteria. | |
| 2.4 | GET `/credit-transactions/history` with query parameters where `fromTs > toTs`. | Returns 400 Bad Request (`CREDIT_TRANSACTION_INVALID_DATE_RANGE`). | |
| 2.5 | GET `/crbt-credit-transaction-service/credit-transactions/export` with header `X-User-Id: 8`. | Returns 200 OK with a downloadable attachment `credit_transactions.csv`. | |
| 2.6 | Inspect CSV file headers. | Headers must exactly be: `ID,User ID,Before Balance,After Balance,Amount,Direction,Gen Type,Model,Is Free,Reason,Reference ID,Timestamp,Created At`. | |
| 2.7 | GET `/auth-service/internal/crbt/user-credit/{msisdn}` for a valid user. | Returns 200 OK with raw `{ "userId": 8, "msisdn": "84987000105" }` bypass object. | |

---

## 3. System Resilience & Event Integrity UAT
**Goal**: Verify bug fixes concerning transactional consistency (RabbitMQ event deferral) and Redis server outage tolerance.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | **Redis Resiliency**: Stop the Redis container (`docker compose stop redis-cache`). | Redis is shut down. | |
| 3.2 | Call AI Music Generation API: `POST /campaigns/generate?genre=Chill&mood=Vui&instrument=Guitar`. | API does not throw 500 error; falls back gracefully to library matches (resilience wrapper verified). | |
| 3.3 | Restart the Redis container (`docker compose start redis-cache`). | Redis is active again. | |
| 3.4 | **RabbitMQ Transactional Integrity**: Cause a deliberate DB rollback during wallet deduction (e.g. database constraint trigger or force thread interruption). | Database balance is rolled back; **NO** transaction event is sent to RabbitMQ (Transactional Outbox sync verified). | |
| 3.5 | Perform a successful wallet deduction. | Database balance is updated; RabbitMQ event is published successfully **after** the DB transaction commits. | |

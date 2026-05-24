# CRBT Core Adapter Specification

## 1. Overview
`crbt-core-adapter` integrates the platform with the external Mytone CMS/CRBT core. It accepts ringtone assignment requests, stores local assignment state, performs async sync to Mytone, and allows users to list/remove assignments.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16
- **Async Execution**: Spring `@Async`
- **External Client**: Spring `RestClient` with Resilience4j CircuitBreaker + Retry
- **Messaging**: RabbitMQ DLQ for exhausted Mytone sync failures
- **Port**: 8094

## 3. Database Schema

### `ringtone_assignments` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Assignment ID |
| `user_id` | BIGINT | NOT NULL | Platform user ID |
| `msisdn` | VARCHAR(20) | NOT NULL | Subscriber phone number |
| `ringtone_url` | VARCHAR(500) | NOT NULL | Source audio URL |
| `status` | VARCHAR(20) | NOT NULL | `PENDING`, `SYNCING`, `ACTIVE`, `FAILED`, `REMOVED` |
| `mytone_transaction_id` | VARCHAR(100) | | External Mytone transaction ID |
| `error_message` | VARCHAR(500) | | Last sync/remove error |
| `retry_count` | INT | NOT NULL | Number of sync failures |
| `created_at` | TIMESTAMP | NOT NULL | Creation time |
| `updated_at` | TIMESTAMP | | Last update time |

## 4. Status Lifecycle
| Status | Meaning |
|---|---|
| `PENDING` | Local assignment saved, async sync not started yet |
| `SYNCING` | Adapter is preparing media and calling Mytone CMS |
| `ACTIVE` | Mytone accepted assignment and returned transaction ID |
| `FAILED` | Mytone sync failed or returned failure response |
| `REMOVED` | Assignment removed from Mytone successfully |

## 5. Business Logic Flows

### 5.1. Assign Ringtone
1. User calls `POST /ringtone-assignments` with `msisdn` and `ringtoneUrl`.
2. Controller resolves current user via `SecurityUtils.getCurrentUserId()`.
3. Service creates `RingtoneAssignment` with status `PENDING`.
4. Service starts `syncToMytoneAsync(assignmentId)`.
5. API returns `202 Accepted` immediately with the local assignment record.

### 5.2. Async Mytone Sync
1. Async worker loads the assignment by ID.
2. Status set to `SYNCING`.
3. Adapter prepares media for Mytone:
   - download from MinIO (placeholder in current implementation),
   - transcode to MP3 128kbps,
   - add ID3 metadata,
   - produce transcoded URL.
4. Adapter calls Mytone CMS `POST /ringtones/assign` with:
   - `msisdn`,
   - transcoded ringtone URL,
   - action `ASSIGN`.
5. If Mytone returns `success=true`:
   - status set to `ACTIVE`,
   - `mytoneTransactionId` saved.
6. If Mytone fails:
   - status set to `FAILED`,
   - `errorMessage` saved,
   - `retryCount` incremented.
7. If `retryCount >= 3`, failure info is sent to the global DLQ exchange.

### 5.3. List Assignments
1. User calls `GET /ringtone-assignments`.
2. Service fetches assignments by `userId`, sorted by `createdAt` descending.
3. Response includes local state and Mytone transaction info.

### 5.4. Remove Assignment
1. User calls `DELETE /ringtone-assignments/{assignmentId}`.
2. Service verifies assignment exists.
3. Service verifies ownership; otherwise throws `COMMON_FORBIDDEN`.
4. Adapter calls Mytone CMS `DELETE /ringtones/{msisdn}`.
5. If Mytone succeeds, status set to `REMOVED`.
6. If Mytone fails, `errorMessage` is updated and current status is preserved.

## 6. External Mytone CMS Client

### Config
| Property | Description |
|---|---|
| `mytone.api.base-url` | Base URL of Mytone CMS API |
| `mytone.api.key` | API key sent as `X-API-Key` header |

### Endpoints Called
| Operation | Method | Path | Notes |
|---|---|---|---|
| Assign ringtone | POST | `/ringtones/assign` | Body: `MytoneCmsRequest` |
| Remove ringtone | DELETE | `/ringtones/{msisdn}` | Path param: subscriber MSISDN |

### Resilience
- `@CircuitBreaker(name = "mytone")`
- `@Retry(name = "mytone")`
- Fallback returns `MytoneCmsResponse(success=false, transactionId=null, message="Mytone unavailable: ...")`.

## 7. RabbitMQ / DLQ
This service does not consume domain events. It uses RabbitMQ only to publish exhausted Mytone sync failures to the common DLQ exchange after 3 failed attempts.

## 8. Error Codes
| Code | HTTP Status | Scenario |
|---|---|---|
| `COMMON_UNAUTHORIZED` | 401 | Missing `X-User-Id` / auth context |
| `COMMON_FORBIDDEN` | 403 | User tries to remove another user's assignment |
| `COMMON_NOT_FOUND` | 404 | Assignment ID does not exist |

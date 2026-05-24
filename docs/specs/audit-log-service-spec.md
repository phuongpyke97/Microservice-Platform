# Audit Log Service Specification

## 1. Overview
`audit-log-service` maintains a comprehensive, immutable audit trail of all security-relevant actions across the platform. It is a pure event consumer with a read-only query API for administrators.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16
- **Messaging**: RabbitMQ (Consumer only)
- **Port**: 8084

## 3. Database Schema

### `audit_logs` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Record ID |
| `user_id` | BIGINT | | User who performed the action (nullable for system actions) |
| `action` | VARCHAR(100) | NOT NULL | Action type (e.g., `LOGIN`, `SUBSCRIBE`, `PAYMENT`) |
| `source_ip` | VARCHAR(50) | | Source IP address |
| `status` | VARCHAR(50) | | Action result (e.g., `SUCCESS`, `FAILED`) |
| `metadata_json` | TEXT | | Additional context as JSON string |
| `timestamp` | BIGINT | NOT NULL | Event timestamp (Unix epoch ms) from source |
| `created_at` | TIMESTAMP | NOT NULL, immutable | Record insertion time |

**Immutability constraint**: No `UPDATE` or `DELETE` is ever issued on this table. Records represent historical facts and must remain unchanged after insertion.

## 4. RabbitMQ Integration

### Consumer
- **Queue**: `audit.log` (DLQ-enabled via `common-rmq`)
- **Exchange**: `audit.events` (Topic)
- **Routing Key**: `audit.log`
- **Event Type**: `AuditLogEvent`

### `AuditLogEvent` Payload
| Field | Type | Description |
|---|---|---|
| `userId` | Long | User who performed the action (nullable) |
| `action` | String | Action type |
| `sourceIp` | String | Source IP address |
| `status` | String | Action result |
| `metadataJson` | String | Additional context as JSON |
| `timestamp` | long | Event timestamp from the source service (Unix epoch ms) |

### Publisher
`audit-log-service` does **not** publish any events.

## 5. Business Logic Flows

### 5.1. Record Insertion (Event-Driven)
1. Any service publishes `AuditLogEvent` to `audit.events` exchange after a security-relevant action.
2. `AuditLogListener` receives the event.
3. `AuditLogService.save()` constructs an `AuditLog` entity and persists it.
4. No response is sent back — this is a fire-and-forget consumer.
5. On failure, the message is retried 3 times (1s/2s/4s backoff) then routed to DLQ.

### 5.2. Query Audit Logs
1. Administrator calls `GET /audit/query` with optional filters.
2. Service builds a JPA `Specification` dynamically from provided filters:
   - `userId`: exact match
   - `action`: exact match
   - `status`: exact match
   - `fromTs`/`toTs`: range filter on `timestamp` field
3. Validation: if `fromTs > toTs`, throw `AUDIT_INVALID_DATE_RANGE`.
4. Results sorted by `timestamp` DESC, returned as `PageResponse`.

## 6. Common Audit Actions
| Action | Triggered By | Status Values |
|---|---|---|
| `LOGIN` | auth-service | `SUCCESS`, `FAILED` |
| `REGISTER` | auth-service | `SUCCESS`, `FAILED` |
| `PASSWORD_RESET` | auth-service | `SUCCESS`, `FAILED` |
| `SUBSCRIBE` | crbt-campaign-service | `SUCCESS`, `FAILED` |
| `PAYMENT` | payment-gateway-service | `SUCCESS`, `FAILED` |
| `CREDIT_DEDUCT` | credit-wallet-service | `SUCCESS`, `FAILED` |
| `CREDIT_ADD` | credit-wallet-service | `SUCCESS` |

## 7. Error Codes
| Code | HTTP Status | Description |
|---|---|---|
| `AUDIT_INVALID_DATE_RANGE` | 400 | `fromTs` is greater than `toTs` |
| `COMMON_UNAUTHORIZED` | 401 | Missing user authentication |

## 8. Integration Points
- **All services**: Any service can publish `AuditLogEvent` for security-relevant actions.
- **Admin UI**: Consumes the query API to display audit trails.

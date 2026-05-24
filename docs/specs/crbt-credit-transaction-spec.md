# CRBT Credit Transaction Service Specification

## 1. Overview
`crbt-credit-transaction-service` maintains a complete, immutable audit trail of every credit change in the system. It is a pure event consumer — it writes records only from RabbitMQ events and provides read-only query endpoints for users to inspect their financial history.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16
- **Messaging**: RabbitMQ (Consumer only)
- **Port**: 8093

## 3. Database Schema

### `credit_transactions` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Record ID |
| `user_id` | BIGINT | NOT NULL | Owner user ID |
| `amount` | INT | NOT NULL | Credit amount (always positive) |
| `direction` | VARCHAR(20) | NOT NULL | `DEBIT` or `CREDIT` |
| `reason` | VARCHAR(100) | NOT NULL | Human-readable change description |
| `reference_id` | VARCHAR(100) | | Optional reference to originating entity |
| `timestamp` | BIGINT | NOT NULL | Event timestamp (Unix epoch ms) from source |
| `created_at` | TIMESTAMP | NOT NULL, immutable | Record insertion time |

**Immutability constraint**: No `UPDATE` or `DELETE` is ever issued on this table. Records represent a historical fact and must remain unchanged after insertion.

## 4. RabbitMQ Integration

### Consumer
- **Queue**: `credit.transaction.history` (DLQ-enabled via `common-rmq`)
- **Exchange**: `credit.events` (Topic)
- **Routing Key**: `credit.changed`
- **Event Type**: `CreditChangedEvent`

### `CreditChangedEvent` Payload
| Field | Type | Description |
|---|---|---|
| `userId` | Long | User whose credit changed |
| `amount` | int | Credit amount (positive integer) |
| `direction` | String | `DEBIT` or `CREDIT` |
| `reason` | String | Change description |
| `referenceId` | String | Reference to originating transaction (e.g., subscription ID) |
| `timestamp` | long | Event timestamp from the source service (Unix epoch ms) |

### Publisher
`crbt-credit-transaction-service` does **not** publish any events.

## 5. Business Logic Flows

### 5.1. Record Insertion (Event-Driven)
1. `credit-wallet-service` publishes `CreditChangedEvent` to `credit.events` exchange after every successful debit/credit operation.
2. `CreditTransactionListener` receives the event.
3. `CreditTransactionService.save()` constructs a `CreditTransaction` entity and persists it.
4. No response is sent back — this is a fire-and-forget consumer.
5. On failure, the message is retried 3 times (1s/2s/4s backoff) then routed to DLQ.

### 5.2. History Query
1. User calls `GET /credit-transactions/history` with optional filters.
2. Service builds a JPA `Specification` dynamically from provided filters:
   - `direction`: exact match
   - `reason`: case-insensitive LIKE `%reason%`
   - `fromTs`/`toTs`: range filter on `timestamp` field
3. Validation: if `fromTs > toTs`, throw `CREDIT_TRANSACTION_INVALID_DATE_RANGE`.
4. Results sorted by `timestamp` DESC, returned as `PageResponse`.

### 5.3. CSV Export
1. User calls `GET /credit-transactions/export` with optional filters.
2. Same filter logic and validation as history query.
3. All matching records fetched (no pagination).
4. Service builds CSV string with header row.
5. Returned as `text/csv` attachment named `credit_transactions.csv`.

## 6. Error Codes
| Code | HTTP Status | Description |
|---|---|---|
| `CREDIT_TRANSACTION_INVALID_DATE_RANGE` | 400 | `fromTs` is greater than `toTs` |
| `COMMON_UNAUTHORIZED` | 401 | Missing user authentication |

## 7. Integration Points
- **credit-wallet-service**: Primary producer of `CreditChangedEvent` messages consumed by this service.
- **crbt-campaign-service**: Also publishes `CreditChangedEvent` during subscription renewal (routed via same exchange/key).

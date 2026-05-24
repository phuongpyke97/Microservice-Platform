# Payment Gateway Service Specification

## 1. Overview
`payment-gateway-service` integrates with the telco Mobile Payment System (MPS) / Charging Core to process payments. It provides idempotent payment charging, stores transaction records, and publishes payment result events.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16
- **External Client**: Feign Client with Resilience4j CircuitBreaker + Retry
- **Messaging**: RabbitMQ (Publisher only)
- **Port**: 8085

## 3. Database Schema

### `payment_transactions` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Transaction ID |
| `idempotency_key` | VARCHAR(64) | NOT NULL, UNIQUE | Idempotency key for duplicate detection |
| `user_id` | BIGINT | NOT NULL | Platform user ID |
| `msisdn` | VARCHAR(20) | NOT NULL | Subscriber phone number |
| `package_code` | VARCHAR(50) | NOT NULL | Package being purchased |
| `amount_mmk` | BIGINT | NOT NULL | Amount charged in Myanmar Kyat |
| `credit_amount` | INT | NOT NULL | Platform credits to grant on success |
| `status` | VARCHAR(20) | NOT NULL | `PENDING`, `SUCCESS`, `FAILED` |
| `mps_ref` | VARCHAR(100) | | External MPS transaction reference |
| `error_message` | VARCHAR(500) | | Error details if status is FAILED |
| `created_at` | TIMESTAMP | NOT NULL | Creation time |
| `updated_at` | TIMESTAMP | | Last update time |

**Unique constraint**: `idempotency_key` ensures duplicate requests return the existing transaction.

## 4. Business Logic Flows

### 4.1. Charge Payment (Idempotent)
1. User calls `POST /api/payments/charge` with `idempotencyKey`, `packageCode`, `amountMmk`, `creditAmount`.
2. Service validates amounts are positive.
3. Service checks if a transaction with this `idempotencyKey` already exists:
   - If exists, return the existing transaction (idempotent behavior).
   - If not, proceed to step 4.
4. Service creates a new `PaymentTransaction` with status `PENDING`.
5. Service calls MPS via Feign client: `POST /charge` with `MpsChargeRequest`.
6. If MPS returns `success=true`:
   - Transaction status set to `SUCCESS`.
   - `mpsRef` saved.
7. If MPS returns `success=false`:
   - Transaction status set to `FAILED`.
   - `errorMessage` saved.
8. Service publishes `PaymentResultEvent` to RabbitMQ.
9. Service returns the transaction to the caller.

### 4.2. Get Transaction by Idempotency Key
1. User calls `GET /api/payments/idempotency/{idempotencyKey}`.
2. Service queries DB by `idempotencyKey`.
3. If found, return the transaction.
4. If not found, throw `PAY_TRANSACTION_NOT_FOUND`.

## 5. External MPS Client

### Config
| Property | Description |
|---|---|
| `mps.endpoint` | Base URL of the MPS API |

### Endpoints Called
| Operation | Method | Path | Notes |
|---|---|---|---|
| Charge payment | POST | `/charge` | Body: `MpsChargeRequest` |

### Resilience
- `@CircuitBreaker(name = "mpsCharge")`
- Fallback throws `PAY_MPS_UNAVAILABLE`.

### `MpsChargeRequest` Schema
| Field | Type | Description |
|---|---|---|
| `msisdn` | String | Subscriber phone number |
| `amountMmk` | long | Amount to charge in Myanmar Kyat |
| `packageCode` | String | Package code |
| `idempotencyKey` | String | Idempotency key |

### `MpsChargeResponse` Schema
| Field | Type | Description |
|---|---|---|
| `success` | boolean | Whether the charge succeeded |
| `providerReference` | String | External transaction reference |
| `message` | String | Error message if failed |

## 6. RabbitMQ Integration

### Publisher
- **Exchange**: `payment.events` (Topic)
- **Routing Key**: `payment.result`
- **Event Type**: `PaymentResultEvent`

### `PaymentResultEvent` Payload
| Field | Type | Description |
|---|---|---|
| `userId` | Long | User who made the payment |
| `transactionId` | String | Payment transaction ID |
| `packageCode` | String | Package code |
| `status` | String | `SUCCESS` or `FAILED` |
| `creditAmount` | int | Credits to grant (0 if failed) |
| `timestamp` | long | Event timestamp (Unix epoch ms) |

### Consumer
`payment-gateway-service` does **not** consume any events.

## 7. Error Codes
| Code | HTTP Status | Description |
|---|---|---|
| `PAY_TRANSACTION_NOT_FOUND` | 404 | Transaction not found by idempotency key |
| `PAY_DUPLICATE_REQUEST` | 409 | Duplicate payment request (not currently thrown due to idempotent behavior) |
| `PAY_MPS_UNAVAILABLE` | 503 | MPS is down or circuit breaker is open |
| `PAY_MPS_REJECTED` | 402 | MPS rejected the payment |
| `PAY_INVALID_AMOUNT` | 400 | Amount or credit is not positive |

## 8. Integration Points
- **crbt-campaign-service**: Calls this service to charge users during subscription.
- **credit-wallet-service**: Listens to `PaymentResultEvent` to credit user wallets on successful payment.
- **notification-service**: Listens to `PaymentResultEvent` to send payment confirmation.

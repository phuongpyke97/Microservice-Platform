# Credit Wallet Service Specification

## 1. Overview
`credit-wallet-service` is an infrastructure service responsible for managing the virtual currency (credits) of users. It handles safe, concurrent addition and deduction of credits using distributed locks.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16 (Dedicated `wallet_db`)
- **Cache/Locking**: Redis Cluster 7.x (via Redisson)
- **Messaging**: RabbitMQ (Publishing/Consuming events)
- **Migration**: Flyway
- **Port**: 8086

## 3. Database Schema

### `wallets` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Wallet record ID |
| `user_id` | BIGINT | NOT NULL, UNIQUE | Owner user ID |
| `balance` | INT | NOT NULL | Current credit balance |
| `version` | BIGINT | | Optimistic locking version (JPA `@Version`) |
| `updated_at`| TIMESTAMP | | Last update timestamp |

## 4. Distributed Locking Strategy
To prevent race conditions during concurrent `add` or `deduct` operations for the same user (e.g., rapid multiple API calls), the service uses **Redisson Distributed Locks**.

- **Lock Key**: `wallet:{userId}`
- **Acquire Timeout**: 3 seconds
- **Lease Time**: 3 seconds
- **Fallback**: If the lock cannot be acquired within 3 seconds, a `WALLET_LOCK_TIMEOUT` exception is thrown to fail fast.

## 5. Business Logic Flows

### 5.1. Add Credit Flow
1. Receives request to add `N` credits for `userId`.
2. Validates `N > 0`.
3. Acquires Redisson lock `wallet:{userId}`.
4. Reads wallet from DB. If the wallet doesn't exist, it creates a new one with `balance = 0`.
5. Adds `N` to the balance.
6. Saves to DB (Optimistic locking checks `version`).
7. Publishes `CreditChangedEvent` to RabbitMQ with action `ADD`.
8. Releases Redisson lock.
9. Returns new balance.

### 5.2. Deduct Credit Flow
1. Receives request to deduct `N` credits from `userId`.
2. Validates `N > 0`.
3. Acquires Redisson lock `wallet:{userId}`.
4. Reads wallet from DB. If not found, throws `WALLET_NOT_FOUND`.
5. Checks if `balance >= N`. If not, throws `WALLET_INSUFFICIENT_CREDIT`.
6. Deducts `N` from balance.
7. Saves to DB (Optimistic locking applies).
8. Publishes `CreditChangedEvent` to RabbitMQ with action `DEDUCT`.
9. Releases Redisson lock.
10. Returns new balance.

### 5.3. Async Payment Processing
The service acts as a consumer for payment success events (e.g., from `payment-gateway-service`).
1. Listens to specific payment queues via `@RabbitListener`.
2. Upon receiving a payment success event, it maps the real currency to credits.
3. Invokes the `addCredit` flow internally.

## 6. Event Publishing (RabbitMQ)
Events are published to `RmqExchanges.CREDIT_EVENTS`.

| Event Type | Routing Key | Payload Structure |
|---|---|---|
| **Credit Added** | `credit.changed` | `CreditChangedEvent(userId, amount, "ADD", reason, referenceId, timestamp)` |
| **Credit Deducted**| `credit.deducted`| `CreditChangedEvent(userId, amount, "DEDUCT", reason, referenceId, timestamp)` |

*Note*: These events are primarily consumed by `crbt-credit-transaction-service` to maintain an immutable ledger of all transactions.

## 7. Configuration Properties
```yaml
spring:
  data:
    redis:
      cluster:
        nodes: ${REDIS_NODES:redis-node-1:6379,redis-node-2:6379,redis-node-3:6379}
```
*Note*: The service requires a Redis Cluster for Redisson to function in production.
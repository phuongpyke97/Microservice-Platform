# Credit Wallet Service UAT Test Cases

## 1. Wallet Balance Inquiry
**Goal**: Verify users can check their own wallet balance.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | GET `/api/wallet/me` for a user with 500 credits. | Returns 200 OK, `balance: 500`. | |
| 1.2 | GET `/api/wallet/me` for a new user without a wallet record in DB. | Returns 404 Not Found, code `WALLET_NOT_FOUND`. | |

## 2. Adding Credit Flow
**Goal**: Verify manual or internal credit addition.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | POST `/api/wallet/{userId}/add` with `amount: 100`. | Returns 200 OK, balance increased by 100. | |
| 2.2 | POST with `amount: -50`. | Returns 400 Bad Request, validation error or `WALLET_INVALID_AMOUNT`. | |
| 2.3 | Concurrent POSTs to `/add` for the same user. | Redisson lock handles sequential processing; final balance is sum of all successful increments. | |
| 2.4 | Verify RabbitMQ `CreditChangedEvent` (ADD). | Event published to `credit-events-exchange`. | |

## 3. Deducting Credit Flow
**Goal**: Verify credit deduction for purchases.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | POST `/api/wallet/{userId}/deduct` with `amount: 50` (Initial balance 100). | Returns 200 OK, new balance 50. | |
| 3.2 | POST `/api/wallet/{userId}/deduct` with `amount: 200` (Initial balance 100). | Returns 400 Bad Request, code `WALLET_INSUFFICIENT_CREDIT`. | |
| 3.3 | Concurrent POSTs to `/deduct` for the same user with total > balance. | At least one request fails with `WALLET_INSUFFICIENT_CREDIT`; balance never goes negative. | |
| 3.4 | Verify RabbitMQ `CreditChangedEvent` (DEDUCT). | Event published to `credit-events-exchange`. | |

## 4. Concurrency & Performance
**Goal**: Verify system robustness under load.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | Simulate a burst of 10 requests to `/deduct` for the same user ID. | Distributed locks ensure sequential DB updates; no double-spending occurs. | |
| 4.2 | Force a long-running transaction to hold a lock, then try a new request. | Second request returns 409 Conflict after 3 seconds, code `WALLET_LOCK_TIMEOUT`. | |

## 5. Integration Scenario
**Goal**: Verify flow from payment to wallet update.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | Mock a `PAYMENT_SUCCESS` event from `payment-gateway-service` for `userId`. | `credit-wallet-service` consumes event and auto-increments balance. | |
| 5.2 | Check wallet balance via `/api/wallet/me`. | Balance reflects the payment amount. | |
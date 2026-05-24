# Payment Gateway Service UAT Test Cases

## 1. Idempotent Payment Charge
**Goal**: Verify payments can be charged and duplicate requests are handled idempotently.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | Generate unique idempotency key (e.g., `tx-123`). | Key generated. | |
| 1.2 | POST `/api/payments/charge` with the key, `packageCode`, `amountMmk=5000`, `creditAmount=500`. | Returns 200 OK. Transaction created with status `SUCCESS` (assuming MPS mock returns true). | |
| 1.3 | Check `payment_transactions` DB. | Record exists with correct fields, `mpsRef` populated. | |
| 1.4 | Check RabbitMQ `payment.events`. | `PaymentResultEvent` published with `status=SUCCESS`, `creditAmount=500`. | |
| 1.5 | POST `/api/payments/charge` again with the exact same request body. | Returns 200 OK immediately with the existing transaction data. No new DB record created. No new MPS call made. | |

## 2. Invalid Payment Requests
**Goal**: Verify input validation.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | POST `/api/payments/charge` with `amountMmk=0`. | Returns 400 Bad Request, code `PAY_INVALID_AMOUNT`. | |
| 2.2 | POST `/api/payments/charge` with `creditAmount=-100`. | Returns 400 Bad Request, code `PAY_INVALID_AMOUNT`. | |
| 2.3 | POST `/api/payments/charge` with missing `idempotencyKey`. | Returns 400 Bad Request. | |

## 3. MPS Rejection
**Goal**: Verify handling of payment failure from the provider.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | Configure MPS mock to return `success=false` and a rejection message. | MPS mock ready. | |
| 3.2 | POST `/api/payments/charge` with a new idempotency key. | Returns 200 OK, but transaction status is `FAILED`. | |
| 3.3 | Check `payment_transactions` DB. | Record exists, `status=FAILED`, `errorMessage` contains the rejection message. | |
| 3.4 | Check RabbitMQ `payment.events`. | `PaymentResultEvent` published with `status=FAILED`, `creditAmount=0`. | |

## 4. Get Transaction by Idempotency Key
**Goal**: Verify transaction lookup.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | GET `/api/payments/idempotency/{idempotencyKey}` for an existing transaction. | Returns 200 OK with transaction details. | |
| 4.2 | GET `/api/payments/idempotency/{nonExistentKey}`. | Returns 404 Not Found, code `PAY_TRANSACTION_NOT_FOUND`. | |

## 5. MPS Unavailable / Circuit Breaker
**Goal**: Verify fallback behavior when MPS is down.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | Stop MPS mock or force timeout. | MPS unavailable. | |
| 5.2 | POST `/api/payments/charge` with a new key. | Returns 503 Service Unavailable, code `PAY_MPS_UNAVAILABLE`. | |
| 5.3 | Check `payment_transactions` DB. | No record created (transaction rolled back). | |
| 5.4 | Check RabbitMQ. | No event published. | |

## 6. End-to-End Payment Flow
**Goal**: Verify payment triggers wallet credit and notifications.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | Note user's wallet balance. | Initial balance recorded. | |
| 6.2 | Perform successful charge via `/api/payments/charge`. | Transaction SUCCESS, event published. | |
| 6.3 | Check wallet balance. | Balance increased by `creditAmount`. | |
| 6.4 | Check notification service logs (or actual sink). | Payment confirmation notification sent for the package. | |
| 6.5 | Check audit logs. | `PAYMENT` action logged with status `SUCCESS`. | |

# Payment Gateway Service API Documentation

## Overview
`payment-gateway-service` integrates with the telco Mobile Payment System (MPS) / Charging Core to process payments. It provides idempotent payment charging and transaction lookup.

**Base URL**: `/api/payments` (Routed through API Gateway: `http://localhost:8080/api/payments`)

**Authentication**: All endpoints require a valid JWT (`X-User-Id` and `X-Msisdn` headers injected by Gateway).

---

## 1. Charge Payment
Initiates a payment charge via the telco MPS. This endpoint is idempotent — duplicate requests with the same `idempotencyKey` return the existing transaction.

- **URL**: `/charge`
- **Method**: `POST`
- **Auth Required**: Yes

### Request Body
```json
{
  "idempotencyKey": "sub-123-20240101",
  "packageCode": "VIP_MONTHLY",
  "amountMmk": 5000,
  "creditAmount": 500
}
```

### Validation
| Field | Constraint |
|---|---|
| `idempotencyKey` | Required, non-blank, max 64 chars |
| `packageCode` | Required, non-blank |
| `amountMmk` | Required, min 1 (Myanmar Kyat) |
| `creditAmount` | Required, min 1 (platform credits to grant on success) |

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "Payment processed",
  "data": {
    "id": 1001,
    "idempotencyKey": "sub-123-20240101",
    "status": "SUCCESS",
    "mpsRef": "MPS-TX-001",
    "packageCode": "VIP_MONTHLY",
    "amountMmk": 5000,
    "creditAmount": 500
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `400 Bad Request`: `PAY_INVALID_AMOUNT` — Amount or credit is not positive.
- `401 Unauthorized`: `COMMON_UNAUTHORIZED` — Missing JWT.
- `503 Service Unavailable`: `PAY_MPS_UNAVAILABLE` — MPS is down or circuit breaker is open.

---

## 2. Get Transaction by Idempotency Key
Retrieves an existing payment transaction by its idempotency key.

- **URL**: `/idempotency/{idempotencyKey}`
- **Method**: `GET`
- **Auth Required**: Yes

### Path Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `idempotencyKey` | string | Yes | Unique idempotency key used during charge |

### Success Response
- **Code**: `200 OK`
- **Body**: Same as charge response.

### Error Responses
- `404 Not Found`: `PAY_TRANSACTION_NOT_FOUND` — No transaction exists with this idempotency key.

---

## PaymentResponse Schema
| Field | Type | Description |
|---|---|---|
| `id` | long | Transaction ID |
| `idempotencyKey` | string | Unique idempotency key |
| `status` | string | `PENDING`, `SUCCESS`, `FAILED` |
| `mpsRef` | string | External MPS transaction reference (populated on success) |
| `packageCode` | string | Package code being purchased |
| `amountMmk` | long | Amount charged in Myanmar Kyat |
| `creditAmount` | int | Platform credits to grant on success |

## Payment Status Lifecycle
- `PENDING`: Transaction created, MPS call not yet made.
- `SUCCESS`: MPS accepted the charge, `mpsRef` populated.
- `FAILED`: MPS rejected the charge, `errorMessage` contains reason.

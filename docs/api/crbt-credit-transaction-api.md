# CRBT Credit Transaction Service API Documentation

## Overview
`crbt-credit-transaction-service` provides a read-only audit trail of all credit changes across the platform. Records are created automatically via RabbitMQ events; users can query and export their transaction history.

**Base URL**: `/credit-transactions` (Routed through API Gateway: `http://localhost:8080/credit-transactions`)

**Authentication**: All endpoints require a valid JWT (`X-User-Id` header injected by Gateway).

---

## 1. Get Transaction History
Retrieves a paginated, filterable list of the current user's credit transactions, sorted by event timestamp descending.

- **URL**: `/history`
- **Method**: `GET`
- **Auth Required**: Yes

### Query Parameters
| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `direction` | string | No | — | Filter by direction: `DEBIT` or `CREDIT`. |
| `reason` | string | No | — | Case-insensitive partial match on reason field. |
| `fromTs` | long | No | — | Start of range (Unix epoch milliseconds, inclusive). |
| `toTs` | long | No | — | End of range (Unix epoch milliseconds, inclusive). |
| `page` | int | No | `0` | Page index (0-based). |
| `size` | int | No | `20` | Page size. |

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 501,
        "userId": 42,
        "amount": 100,
        "direction": "DEBIT",
        "reason": "Subscribe to package VIP",
        "referenceId": "sub-789",
        "timestamp": 1704067200000,
        "createdAt": "2024-01-01T12:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2024-01-01T12:00:05Z"
}
```

### Error Responses
- `400 Bad Request`: `CREDIT_TRANSACTION_INVALID_DATE_RANGE` — `fromTs` is greater than `toTs`.
- `401 Unauthorized`: `COMMON_UNAUTHORIZED` — Missing or invalid JWT.

---

## 2. Export Transaction History as CSV
Downloads all matching transactions as a CSV file (no pagination; returns all results).

- **URL**: `/export`
- **Method**: `GET`
- **Auth Required**: Yes

### Query Parameters
Same filter parameters as `/history`, excluding `page` and `size`.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `direction` | string | No | — | Filter by direction: `DEBIT` or `CREDIT`. |
| `reason` | string | No | — | Case-insensitive partial match on reason. |
| `fromTs` | long | No | — | Start of range (Unix epoch milliseconds). |
| `toTs` | long | No | — | End of range (Unix epoch milliseconds). |

### Success Response
- **Code**: `200 OK`
- **Content-Type**: `text/csv`
- **Content-Disposition**: `attachment; filename="credit_transactions.csv"`
- **Body** (raw CSV):
```
ID,User ID,Amount,Direction,Reason,Reference ID,Timestamp,Created At
501,42,100,DEBIT,"Subscribe to package VIP",sub-789,1704067200000,2024-01-01T12:00:00Z
```

### Error Responses
- `400 Bad Request`: `CREDIT_TRANSACTION_INVALID_DATE_RANGE` — `fromTs` is greater than `toTs`.
- `401 Unauthorized`: `COMMON_UNAUTHORIZED` — Missing or invalid JWT.

---

## CreditTransactionResponse Schema
| Field | Type | Description |
|---|---|---|
| `id` | long | Record ID. |
| `userId` | long | Owner user ID. |
| `amount` | int | Credit amount (always positive). |
| `direction` | string | `DEBIT` (credit removed) or `CREDIT` (credit added). |
| `reason` | string | Human-readable description of the change. |
| `referenceId` | string | Optional reference to the originating entity (e.g., subscription ID). |
| `timestamp` | long | Event timestamp as Unix epoch milliseconds. |
| `createdAt` | ISO-8601 | Record insertion time. |

---

## Data Source
Records are **never created by HTTP calls**. All records originate from `CreditChangedEvent` messages consumed from RabbitMQ queue `credit.transaction.history`. Records are **immutable** — no UPDATE or DELETE is ever performed after insertion.

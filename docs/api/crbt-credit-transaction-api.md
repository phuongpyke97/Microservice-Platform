# CRBT Credit Transaction Service API Documentation

## Overview
`crbt-credit-transaction-service` provides a read-only audit trail of all credit changes across the platform. Records are created automatically via RabbitMQ events; users can query and export their transaction history.

**Base URL**: `/credit-transactions` (Routed through API Gateway: `http://localhost:8080/credit-transactions`)

**Authentication**: All endpoints require a valid JWT (`X-User-Id` header injected by Gateway).

---

## 1. Get Transaction History
Retrieves a paginated, filterable list of credit transactions, sorted by event timestamp descending. If the caller has ADMIN role, they can pass `msisdn` to view another user's transactions, or leave it empty to retrieve global transactions.

- **URL**: `/history`
- **Method**: `GET`
- **Auth Required**: Yes

### Query Parameters
| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `msisdn` | string | No | — | Phone number of the user to filter (only ADMIN role allowed). |
| `direction` | string | No | — | Filter by direction: `ADD` or `DEDUCT`. |
| `reason` | string | No | — | Case-insensitive partial match on reason field. |
| `fromTs` | string | No | — | Start of range (Date string like `yyyy-MM-dd` or `yyyy-MM-dd HH:mm:ss`, inclusive). |
| `toTs` | string | No | — | End of range (Date string like `yyyy-MM-dd` or `yyyy-MM-dd HH:mm:ss`, inclusive). |
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
        "direction": "DEDUCT",
        "reason": "AI Music Generation",
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
- `400 Bad Request`: `COMMON_INVALID_INPUT` / `CREDIT_TRANSACTION_INVALID_DATE_RANGE` — Invalid date formatting or `fromTs` range.
- `401 Unauthorized`: `COMMON_UNAUTHORIZED` — Missing or invalid JWT.

---

## 2. Export Transaction History as CSV
Downloads matching transactions as a CSV file (no pagination; returns all results).

- **URL**: `/export`
- **Method**: `GET`
- **Auth Required**: Yes

### Query Parameters
Same filter parameters as `/history`, excluding `page` and `size`.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `msisdn` | string | No | — | Phone number of the user to filter (only ADMIN role allowed). |
| `direction` | string | No | — | Filter by direction: `ADD` or `DEDUCT`. |
| `reason` | string | No | — | Case-insensitive partial match on reason. |
| `fromTs` | string | No | — | Start of range (Date string). |
| `toTs` | string | No | — | End of range (Date string). |

### Success Response
- **Code**: `200 OK`
- **Content-Type**: `text/csv`
- **Content-Disposition**: `attachment; filename="credit_transactions.csv"`
- **Body** (raw CSV):
```
ID,User ID,Before Balance,After Balance,Amount,Direction,Gen Type,Model,Is Free,Reason,Reference ID,Timestamp,Created At
501,42,150,50,100,DEDUCT,AI,lyria-3-pro-preview,false,"AI Music Generation",sub-789,1704067200000,2024-01-01T12:00:00Z
```

### Error Responses
- `400 Bad Request`: `COMMON_INVALID_INPUT` / `CREDIT_TRANSACTION_INVALID_DATE_RANGE` — Invalid date formatting or range.
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

# Audit Log Service API Documentation

## Overview
`audit-log-service` provides a read-only query interface for security audit logs. All audit records are created via RabbitMQ events; this API allows administrators to search and filter the audit trail.

**Base URL**: `/audit` (Routed through API Gateway: `http://localhost:8080/audit`)

**Authentication**: All endpoints require a valid JWT (`X-User-Id` header injected by Gateway).

---

## 1. Query Audit Logs
Retrieves a paginated, filterable list of audit log entries, sorted by event timestamp descending.

- **URL**: `/query`
- **Method**: `GET`
- **Auth Required**: Yes

### Query Parameters
| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `userId` | long | No | — | Filter by user ID. |
| `action` | string | No | — | Filter by action type (e.g., `LOGIN`, `SUBSCRIBE`, `PAYMENT`). |
| `status` | string | No | — | Filter by status (e.g., `SUCCESS`, `FAILED`). |
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
        "id": 1001,
        "userId": 42,
        "action": "LOGIN",
        "sourceIp": "192.168.1.100",
        "status": "SUCCESS",
        "metadataJson": "{\"userAgent\":\"Mozilla/5.0\"}",
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
- `400 Bad Request`: `AUDIT_INVALID_DATE_RANGE` — `fromTs` is greater than `toTs`.
- `401 Unauthorized`: `COMMON_UNAUTHORIZED` — Missing or invalid JWT.

---

## AuditLogResponse Schema
| Field | Type | Description |
|---|---|---|
| `id` | long | Audit log record ID. |
| `userId` | long | User who performed the action (nullable for system actions). |
| `action` | string | Action type (e.g., `LOGIN`, `SUBSCRIBE`, `PAYMENT`). |
| `sourceIp` | string | Source IP address of the request. |
| `status` | string | Action result (e.g., `SUCCESS`, `FAILED`). |
| `metadataJson` | string | Additional context as JSON string. |
| `timestamp` | long | Event timestamp as Unix epoch milliseconds. |
| `createdAt` | ISO-8601 | Record insertion time. |

---

## Data Source
Records are **never created by HTTP calls**. All records originate from `AuditLogEvent` messages consumed from RabbitMQ queue `audit.log`. Records are **immutable** — no UPDATE or DELETE is ever performed after insertion.

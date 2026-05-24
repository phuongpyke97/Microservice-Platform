# CRBT Core Adapter API Documentation

## Overview
`crbt-core-adapter` bridges the platform with the external Mytone CMS/CRBT core. It stores ringtone assignment requests locally, syncs them to Mytone, and exposes assignment history/removal APIs.

**Base URL**: `/ringtone-assignments` (Routed through API Gateway: `http://localhost:8080/ringtone-assignments`)

**Authentication**: All endpoints require a valid JWT (`X-User-Id` header injected by Gateway).

---

## 1. Assign Ringtone
Creates a local assignment record and starts asynchronous sync to Mytone CMS.

- **URL**: `/`
- **Method**: `POST`
- **Auth Required**: Yes

### Request Body
```json
{
  "msisdn": "+84901234567",
  "ringtoneUrl": "minio://audio-bucket/ringtone-123.mp3"
}
```

### Validation
| Field | Constraint |
|---|---|
| `msisdn` | Required, regex `^\+?[0-9]{8,15}$` |
| `ringtoneUrl` | Required, non-blank |

### Success Response
- **Code**: `202 Accepted`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "id": 1001,
    "msisdn": "+84901234567",
    "ringtoneUrl": "minio://audio-bucket/ringtone-123.mp3",
    "status": "PENDING",
    "mytoneTransactionId": null,
    "errorMessage": null,
    "createdAt": "2024-01-01T12:00:00Z"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Responses
- `400 Bad Request`: Validation error.
- `401 Unauthorized`: `COMMON_UNAUTHORIZED`.

---

## 2. List Assignments
Returns all ringtone assignments for the current user, sorted by `createdAt` descending.

- **URL**: `/`
- **Method**: `GET`
- **Auth Required**: Yes

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": [
    {
      "id": 1001,
      "msisdn": "+84901234567",
      "ringtoneUrl": "minio://audio-bucket/ringtone-123.mp3",
      "status": "ACTIVE",
      "mytoneTransactionId": "MYTONE-TX-001",
      "errorMessage": null,
      "createdAt": "2024-01-01T12:00:00Z"
    }
  ],
  "timestamp": "2024-01-01T12:00:05Z"
}
```

---

## 3. Remove Assignment
Removes a ringtone assignment from Mytone CMS and marks the local assignment as `REMOVED` if the external call succeeds.

- **URL**: `/{assignmentId}`
- **Method**: `DELETE`
- **Auth Required**: Yes

### Path Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `assignmentId` | long | Yes | Assignment ID to remove |

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": {
    "id": 1001,
    "msisdn": "+84901234567",
    "ringtoneUrl": "minio://audio-bucket/ringtone-123.mp3",
    "status": "REMOVED",
    "mytoneTransactionId": "MYTONE-TX-001",
    "errorMessage": null,
    "createdAt": "2024-01-01T12:00:00Z"
  },
  "timestamp": "2024-01-01T12:00:10Z"
}
```

### Error Responses
- `401 Unauthorized`: `COMMON_UNAUTHORIZED`.
- `403 Forbidden`: `COMMON_FORBIDDEN` — assignment belongs to another user.
- `404 Not Found`: `COMMON_NOT_FOUND` — assignment does not exist.

---

## AssignmentResponse Schema
| Field | Type | Description |
|---|---|---|
| `id` | long | Assignment ID |
| `msisdn` | string | Subscriber phone number |
| `ringtoneUrl` | string | Source ringtone URL |
| `status` | string | `PENDING`, `SYNCING`, `ACTIVE`, `FAILED`, `REMOVED` |
| `mytoneTransactionId` | string | External Mytone transaction ID when successful |
| `errorMessage` | string | Error details when sync/remove fails |
| `createdAt` | ISO-8601 | Assignment creation time |

## Status Lifecycle
- `PENDING`: Local record created.
- `SYNCING`: Background sync is calling Mytone CMS.
- `ACTIVE`: Mytone assignment succeeded.
- `FAILED`: Mytone assignment failed.
- `REMOVED`: Mytone removal succeeded.
